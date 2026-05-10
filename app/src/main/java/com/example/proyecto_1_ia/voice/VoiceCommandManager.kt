package com.example.proyecto_1_ia.voice

import android.content.Context
import android.util.Log
import kotlin.math.sin

//Nuestras librerías creadas
import com.example.proyecto_1_ia.ml.OnnxInferenceEngine

class VoiceCommandManager(
    private val context: Context,
    private val onCommandDetected: (String, Float) -> Unit  //Pasamos el comando y nivel de confianza
){
    companion object {
        private const val TAG = "VoiceCommandManager"
        private const val CONFIDENCE_THRESHOLD = 0.2f   //Solo se activa si hay 60% de confianza que se escuchó algo
    }

    private val inferenceEngine = OnnxInferenceEngine(context)
    private var isModelLoaded = false                   //Variable para determinar si el modelo fue cargado

    //Función ejecutada al inicio de ser llamada la clase
    init {
        loadModel()
    }

    //Función encargada de cargar el modelo y preparar todo el reconocimiento de voz
    private fun loadModel(){
        //Primero validamos que no sea ser que ya previamente se ha cargado
        isModelLoaded = inferenceEngine.loadModel()
        if (isModelLoaded){
            Log.d(TAG, "Modelo ONNX cargado de forma exitosa")
            testAllCommandAudioFiles()
        } else {
            Log.e(TAG, "Voice Command Manager error (Falló en cargar el modelo ONNX)")
        }
    }

    /*
    * Función para testear todos los archivos .wav de assets/test_audio
    * Los archivos tienen nombre comando .wav (yes.wav, no.wav, etc)
    */
    private fun testAllCommandAudioFiles() {
        Log.d(TAG, "============================================")
        Log.d(TAG, "   TESTING ALL COMMAND AUDIO FILES")
        Log.d(TAG, "============================================")

        val commands = listOf("yes", "no", "up", "down", "left", "right", "on", "off", "stop", "go")

        for (cmd in commands) {
            testAudioFile(cmd, "test_audio/$cmd.wav")
        }

        Log.d(TAG, "============================================")
        Log.d(TAG, "   AUDIO FILE TESTS COMPLETE")
        Log.d(TAG, "============================================")
    }

    /*
    * Función encargada de cargar un archivo .wav de assets, extrae PCM samples y hace una predicción del test
    */
    private fun testAudioFile(expectedCommand: String, assetPath: String) {
        try {
            val audioData = loadWavFromAssets(assetPath)
            if (audioData == null) {
                Log.w(TAG, "Could not load: $assetPath")
                return
            }

            Log.d(TAG, "")
            Log.d(TAG, "--- Testing: $expectedCommand ($assetPath) ---")

            // Read sample rate from the file to determine if resampling is needed
            // For now, try without resampling first, and also check the original sample rate
            val bytes = context.assets.open(assetPath).readBytes()
            val headerBuffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            headerBuffer.position(24)
            val originalSampleRate = headerBuffer.int

            Log.d(TAG, "Original: ${audioData.size} samples @ $originalSampleRate Hz")

            // Resample if needed
            val resampled = if (originalSampleRate != 16000) {
                Log.d(TAG, "Resampling from $originalSampleRate to 16000...")
                resampleTo16kHz(audioData, originalSampleRate)
            } else {
                audioData
            }

            Log.d(TAG, "After resample: ${resampled.size} samples, min=${resampled.minOrNull()}, max=${resampled.maxOrNull()}")

            // Pad/truncate to exactly 16000
            val padded = when {
                resampled.size < 16000 -> resampled + FloatArray(16000 - resampled.size)
                resampled.size > 16000 -> resampled.copyOf(16000)
                else -> resampled
            }

            val result = inferenceEngine.predictFromAudio(padded)

            if (result != null) {
                val (command, confidence) = result
                val isCorrect = command == expectedCommand
                val status = if (isCorrect) "✅ CORRECT" else "❌ WRONG"
                Log.d(TAG, "$status - Expected: $expectedCommand, Got: $command (${(confidence * 100).toInt()}%)")
            } else {
                Log.e(TAG, "❌ ERROR - predictFromAudio returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error testing $expectedCommand: ${e.message}")
        }
    }

    /*
    * Función encargada de cargar y parsear un archivo wav a un 16-bit PCM mono
    */
    private fun loadWavFromAssets(assetPath: String): FloatArray? {
        return try {
            val bytes = context.assets.open(assetPath).readBytes()

            val riff = String(bytes, 0, 4)
            val wave = String(bytes, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") {
                Log.e(TAG, "Not a valid WAV file: $assetPath")
                return null
            }

            // Read header using ByteBuffer for correct endianness
            val headerBuffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)

            // Skip to format chunk
            headerBuffer.position(20)
            val audioFormat = headerBuffer.short.toInt() and 0xFFFF
            val numChannels = headerBuffer.short.toInt() and 0xFFFF
            val sampleRate = headerBuffer.int  // This should be positive now!
            headerBuffer.position(34)
            val bitsPerSample = headerBuffer.short.toInt() and 0xFFFF

            Log.d(TAG, "WAV: format=$audioFormat, channels=$numChannels, sampleRate=$sampleRate, bits=$bitsPerSample")

            // Find data chunk
            var dataOffset = 36
            while (dataOffset < bytes.size - 8) {
                val chunkId = String(bytes, dataOffset, 4)
                val chunkSize = (bytes[dataOffset + 4].toInt() and 0xFF) or
                        (bytes[dataOffset + 5].toInt() shl 8) or
                        (bytes[dataOffset + 6].toInt() shl 16) or
                        (bytes[dataOffset + 7].toInt() shl 24)
                if (chunkId == "data") {
                    dataOffset += 8
                    break
                }
                dataOffset += 8 + chunkSize
            }

            // Read samples
            val numSamples = (bytes.size - dataOffset) / (bitsPerSample / 8)
            val samples = FloatArray(numSamples)
            val sampleBuffer = java.nio.ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until numSamples) {
                samples[i] = sampleBuffer.short.toFloat() / 32768f
            }

            samples
        } catch (e: Exception) {
            Log.e(TAG, "Error loading WAV: ${e.message}")
            null
        }
    }

    /**
     * Simple linear resampling from sourceRate to 16000
     */
    private fun resampleTo16kHz(samples: FloatArray, sourceRate: Int): FloatArray {
        if (sourceRate == 16000) return samples

        val ratio = 16000.0 / sourceRate
        val newLength = (samples.size * ratio).toInt()
        val resampled = FloatArray(newLength)

        for (i in 0 until newLength) {
            val srcIndex = i / ratio
            val srcFloor = srcIndex.toInt()
            val srcCeil = minOf(srcFloor + 1, samples.size - 1)
            val frac = srcIndex - srcFloor

            resampled[i] = samples[srcFloor] * (1 - frac).toFloat() + samples[srcCeil] * frac.toFloat()
        }

        return resampled
    }

    /**
     * FUNCIÓN ENCARGADA DE PROCESAR AUDIO HACIA EL PIPELINE COMPLETO
     * AUDIO ->  MEL SPECTROGRAM -> ONNX INFERENCE -> COMMAND
     */
    fun processAudio(audioData: FloatArray){
        Log.d(TAG, "processAudio called with ${audioData.size} samples")
        //Validamos que el modelo haya sido cargado
        if (!isModelLoaded){
            Log.e(TAG, "Modelo no cargó, no puede procesarse el audio")
            return
        }

        Log.d(TAG, "=== MIC AUDIO ===")
        Log.d(TAG, "Samples: ${audioData.size}")
        Log.d(TAG, "Min: ${audioData.minOrNull()}, Max: ${audioData.maxOrNull()}, Mean: ${audioData.average().toFloat()}")
        Log.d(TAG, "First 20: ${audioData.take(20).joinToString { "%.4f".format(it) }}")

        // Check if audio is silent (all zeros or very quiet)
        val maxAbs = audioData.maxOf { kotlin.math.abs(it) }
        Log.d(TAG, "Max absolute value: $maxAbs")
        if (maxAbs < 0.01f) {
            Log.e(TAG, "⚠️ AUDIO IS TOO QUIET - Microphone may not be capturing sound!")
        }

        //No ocupamos truncal
        val padded = when {
            audioData.size < 16000 -> {
                Log.d(TAG, "Padding from ${audioData.size} to 16000")
                audioData + FloatArray(16000 - audioData.size)
            }
            audioData.size > 16000 -> {
                Log.d(TAG, "Truncating from ${audioData.size} to 16000")
                audioData.copyOf(16000)
            }
            else -> audioData
        }

        //Llamamos a la función a que se encargue de predecir por audio
        val result = inferenceEngine.predictFromAudio(padded)

        //Checamos validez del resultado
        if (result != null) {
            val (command, confidence) = result
            Log.d(TAG, "Se detectó el comando: $command (confianza: $confidence)")

            //3. Esto solo lo ejecutamos en caso que el nivel de confidencia es superior al threshold
            if (confidence >= CONFIDENCE_THRESHOLD) {
                //Activamos la llamada al comando
                onCommandDetected(command, confidence)
            } else {
                Log.d(TAG, "Nivel de similitud muy bajo: $confidence, threshold: $CONFIDENCE_THRESHOLD")
            }
        }
        /*
        try {
            //1. Primero pasamos el Audio a su versión en Espectrograma
            val features = featureExtractor.audioToMelSpectogram(audioData)

            //2. Ejecutamos la inferencia de ONNX y checamos caso de coincidencia con el comando
            val result = inferenceEngine.predict(features)

            if (result != null){
                //Sí se encontraron coincidencias
                val (command, confidence) = result

                Log.d(TAG, "Se detectó el comando: $command (confianza: $confidence)")

                //3. Esto solo lo ejecutamos en caso que el nivel de confidencia es superior al threshold
                if (confidence >= CONFIDENCE_THRESHOLD) {
                    //Activamos la llamada al comando
                    onCommandDetected(command, confidence)
                } else {
                    Log.d(TAG, "Nivel de similitud muy bajo: $confidence, threshold: $CONFIDENCE_THRESHOLD")
                }
            }
        } catch (e: Exception){
            Log.e(TAG, "Voice Command Manager error (Error procesando audio: ${e.message})")
        }
        */
    }

    //Función encargada de cerrar el modelo
    fun close(){
        inferenceEngine.close()
    }
}