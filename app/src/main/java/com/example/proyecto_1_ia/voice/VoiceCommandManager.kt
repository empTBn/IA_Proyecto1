package com.example.proyecto_1_ia.voice

import android.content.Context
import android.util.Log

//Nuestras librerías creadas
import com.example.proyecto_1_ia.audio.AudioFeatureExtractor
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
    private val featureExtractor = AudioFeatureExtractor()
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
        } else {
            Log.e(TAG, "Voice Command Manager error (Falló en cargar el modelo ONNX)")
        }
    }

    /**
     * FUNCIÓN ENCARGADA DE PROCESAR AUDIO HACIA EL PIPELINE COMPLETO
     * AUDIO ->  MEL SPECTROGRAM -> ONNX INFERENCE -> COMMAND
     */
    fun processAudio(audioData: FloatArray){
        Log.d(TAG, "processAudio called with ${audioData.size} samples")

        //Validamos que el modelo haya sido cargado
        if (!isModelLoaded){
            Log.e(TAG, "Voice Command Manager error (Modelo no cargó, no puede procesarse el audio)")
            return
        }

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
    }

    //Función encargada de cerrar el modelo
    fun close(){
        inferenceEngine.close()
    }
}