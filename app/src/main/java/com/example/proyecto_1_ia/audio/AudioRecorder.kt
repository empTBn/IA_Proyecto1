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

class AudioRecorder(private val context: Context){
    //Acá creamos la clase para poder grabar el audio en buena calidad
    companion object{
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BUFFER_SIZE = 16000               //1 segundo de audio
        const val RECORDING_DURATION_MS = 1000L     //Grabamos 1 segundo por cada inferencia
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
            context,
            Manifest.permission.RECORD_AUDIO
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
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            // Use the larger of minBufferSize or our BUFFER_SIZE
            val bufferSize = maxOf(minBufferSize, BUFFER_SIZE)

            try {
                // Initialize AudioRecord
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    audioRecord?.release()
                    audioRecord = null
                    return@withContext null
                }

                // Start recording
                audioRecord?.startRecording()
                isRecording = true
                Log.d(TAG, "Recording started")

                // Buffer to hold audio data
                val audioBuffer = ShortArray(BUFFER_SIZE)
                var totalSamplesRead = 0

                // Read audio data
                while (totalSamplesRead < BUFFER_SIZE && isRecording) {
                    val samplesRead = audioRecord?.read(
                        audioBuffer,
                        totalSamplesRead,
                        BUFFER_SIZE - totalSamplesRead
                    ) ?: 0

                    if (samplesRead > 0) {
                        totalSamplesRead += samplesRead
                    } else if (samplesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "AudioRecord: ERROR_INVALID_OPERATION")
                        break
                    } else if (samplesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord: ERROR_BAD_VALUE")
                        break
                    }
                }

                // Stop recording
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                isRecording = false

                Log.d(TAG, "Recording stopped. Samples read: $totalSamplesRead")

                if (totalSamplesRead > 0) {
                    // Convert ShortArray to normalized FloatArray (-1.0 to 1.0)
                    val floatData = FloatArray(totalSamplesRead) { i ->
                        audioBuffer[i].toFloat() / Short.MAX_VALUE.toFloat()
                    }

                    // Notify listener
                    onAudioCaptured?.invoke(floatData)

                    floatData
                } else {
                    Log.e(TAG, "No audio data captured")
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
                    Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
                }
                audioRecord = null
                isRecording = false
            }
        }
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



