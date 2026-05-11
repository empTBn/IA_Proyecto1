package com.example.proyecto_1_ia.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.abs

class AudioRecorder(private val context: Context){
    //Acá creamos la clase para poder grabar el audio en buena calidad
    companion object{
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val RECORD_DURATION_SECONDS = 2                               // Record 2 seconds
        const val BUFFER_SIZE = SAMPLE_RATE * RECORD_DURATION_SECONDS       //2 segundos de audio
        const val TARGET_SIZE = SAMPLE_RATE                                 //Extraemos 1 segundo para el modelo
    }

    //Variables para setear el grabador de voz, booleano para indicar si se graba, etc
    private var audioRecord: AudioRecord? = null
    private var isRecording: Boolean = false
    private var recordingJob: Job? = null

    //Callback para cuando el audio sea capturado (usar con ONNX después)
    private var onAudioCaptured: ((FloatArray) -> Unit)? = null

    //Checamos la función para solicitar permisos de grabar audio
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    //Será usado para el audio en el ONNX model
    fun setOnAudioCapturedListener(listener: (FloatArray) -> Unit) {
        this.onAudioCaptured = listener
    }

    //Graba 1 segundo de audio y retorna los datos como RAW PCM en Float Array
    suspend fun recordAudio(): FloatArray? {
        if (!hasPermission()) {
            Log.e(TAG, "No audio recording permission")
            return null
        }

        return withContext(Dispatchers.IO) {
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            )
            // Use the larger of minBufferSize or our BUFFER_SIZE
            val bufferSize = maxOf(minBufferSize, BUFFER_SIZE)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.UNPROCESSED,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    // Fallback to MIC if UNPROCESSED not available
                    Log.w(TAG, "UNPROCESSED not available, trying MIC")
                    audioRecord?.release()
                    audioRecord = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                }

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    audioRecord?.release()
                    audioRecord = null
                    return@withContext null
                }

                //Agarramos el sample rate ACTUAL que el dispositivo usa
                val actualSampleRate = audioRecord?.sampleRate ?: SAMPLE_RATE
                Log.d(TAG, "Recording ${RECORD_DURATION_SECONDS}s @ $actualSampleRate Hz")

                // Start recording
                audioRecord?.startRecording()
                isRecording = true
                Log.d(TAG, "Recording started @ $actualSampleRate Hz")

                // Buffer to hold audio data
                val audioBuffer = ShortArray(BUFFER_SIZE)
                var totalSamplesRead = 0

                // Read audio data
                while (totalSamplesRead < BUFFER_SIZE && isRecording) {
                    val samplesRead = audioRecord?.read(
                        audioBuffer, totalSamplesRead, BUFFER_SIZE - totalSamplesRead
                    ) ?: 0

                    if (samplesRead > 0) {
                        totalSamplesRead += samplesRead
                    } else if (samplesRead == AudioRecord.ERROR_INVALID_OPERATION ||
                        samplesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord error: $samplesRead")
                        break
                    }
                }

                // Stop recording
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                isRecording = false

                Log.d(TAG, "Recording stopped. Samples read: $totalSamplesRead")

                if (totalSamplesRead >= TARGET_SIZE) {
                    // Convert ShortArray to normalized FloatArray (-1.0 to 1.0)
                    val floatData = FloatArray(totalSamplesRead) { i ->
                        audioBuffer[i].toFloat() / Short.MAX_VALUE.toFloat()
                    }

                    //Encontramos el mejor segmento de sonido de lo grabado
                    val bestSegment = extractLoudestSegment(floatData, totalSamplesRead)
                    Log.d(TAG, "Best segment: ${bestSegment.size} samples, max=${bestSegment.maxOf { abs(it) }}")

                    //Aplicamos un resample en caso el actual rate difiera a 16000
                    val finalData = if (actualSampleRate != SAMPLE_RATE) {
                        Log.d(TAG, "Resampling from $actualSampleRate to $SAMPLE_RATE")
                        resample(bestSegment, actualSampleRate, SAMPLE_RATE)
                    } else {
                        bestSegment
                    }

                    //Aplicamos un truncal o pad para hacer que el nivel del rate sea equivalente a SAMPLE_RATE
                    val padded = when {
                        finalData.size < TARGET_SIZE ->
                            finalData + FloatArray(TARGET_SIZE - finalData.size)
                        finalData.size > TARGET_SIZE ->
                            finalData.copyOf(TARGET_SIZE)
                        else -> finalData
                    }

                    // Notify listener
                    Log.d(TAG, "Output: ${padded.size} samples, max=${padded.maxOf { abs(it) }}")
                    onAudioCaptured?.invoke(padded)
                    padded
                } else {
                    Log.e(TAG, "Not enough audio: $totalSamplesRead < $TARGET_SIZE")
                    null
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error recording audio: ${e.message}")
                null
            } finally {
                // Clean up
                try {
                    if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord?.stop()
                    }
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing: ${e.message}")
                }
                audioRecord = null
                isRecording = false
            }
        }
    }

    //Función para extraer el segundo con más sonido o mayor energía
    private fun extractLoudestSegment(samples: FloatArray, totalSamples: Int): FloatArray {
        val windowSize = TARGET_SIZE
        val stepSize = SAMPLE_RATE / 4  // 0.25 second steps

        var bestStart = 0
        var bestEnergy = 0f

        var start = 0
        while (start + windowSize <= totalSamples) {
            var energy = 0f
            for (i in start until start + windowSize) {
                energy += samples[i] * samples[i]
            }

            if (energy > bestEnergy) {
                bestEnergy = energy
                bestStart = start
            }

            start += stepSize
        }

        Log.d(TAG, "Loudest window: start=$bestStart, energy=${"%.2f".format(bestEnergy)}")
        Log.d(TAG, "Segment max: ${samples.copyOfRange(bestStart, bestStart + windowSize).maxOf { abs(it) }}")

        return samples.copyOfRange(bestStart, bestStart + windowSize)
    }

    //Función para aplicar el resample
    private fun resample(samples: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return samples
        val ratio = toRate.toFloat() / fromRate
        val newLength = (samples.size * ratio).toInt()
        val resampled = FloatArray(newLength)
        for (i in 0 until newLength) {
            val srcIndex = i / ratio
            val srcFloor = srcIndex.toInt()
            val srcCeil = minOf(srcFloor + 1, samples.size - 1)
            val frac = srcIndex - srcFloor
            resampled[i] = samples[srcFloor] * (1 - frac) + samples[srcCeil] * frac
        }
        return resampled
    }

    //Iniciar grabación continua
    fun startContinuousRecording(scope: CoroutineScope){
        //Si ya está grabando detenerse
        if (isRecording) return

        recordingJob = scope.launch(Dispatchers.IO){
            while (isActive){
                try {
                    val audioData = recordAudio()
                    if (audioData != null) {
                        //TODO (Agregar interacción con ONNX acá)
                        Log.d(TAG, "Audio capturado: ${audioData.size} samples")
                    }
                    //Pequeño delay entre grabaciones
                    delay(300)
                } catch (e: Exception){
                    Log.e(TAG, "Error continuo en grabación: ${e.message}")
                    delay(1000)
                }
            }
        }
    }

    //Detener grabación continua
    fun stopContinuousRecording(){
        recordingJob?.cancel()
        recordingJob = null
        isRecording = false

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        }

        audioRecord = null
    }

    //Booleano para determinar si sigue grabando
    fun isCurrentlyRecording(): Boolean = isRecording

    //Valor para soltar recursos
    fun release(){
        stopContinuousRecording()
        onAudioCaptured = null
    }
}