package com.example.proyecto_1_ia.ml

import kotlin.math.exp
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession //Librería completa de onnx
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.io.File

class OnnxInferenceEngine(private val context: Context) {
    private var session: OrtSession? = null
    private var preprocessSession: OrtSession? = null
    private var environment: OrtEnvironment? = null

    //Creamos un objeto companion para manejar la solicitud de comandos
    companion object {
        private const val TAG = "OnnxInferenceEngine"  //Colocamos un TAG para manejar errores
        const val MODEL_FILENAME = "model.onnx"     //Nombre del archivo de modelos
        const val PREPROCESS_FILENAME = "preprocessor.onnx"
        const val INPUT_NAME = "input"              //Para combinar con nuestro export
        const val OUTPUT_NAME = "output"            //Para combinar con nuestro export
        const val INPUT_SIZE = 64 * 64              //Para el espectrograma MEL 64x64

        //Lista de comandos (siguiendo el orden de los de entrenamiento)
        val COMMANDS = listOf(
            "yes", "no",
            "up", "down", "left", "right",
            "on", "off",
            "stop", "go"
        )
    }

    //Función encargada para precargar archivos ONNX
    private fun loadOnnxFile(filename: String): OrtSession? {
        val modelDir = context.filesDir
        val file = File(modelDir, filename)
        val dataFile = File(modelDir, "$filename.data")

        context.assets.open(filename).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        try {
            context.assets.open("$filename.data").use { input ->
                dataFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (_: Exception) {}

        val opts = OrtSession.SessionOptions()
        opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        opts.addCPU(true)

        return environment?.createSession(file.absolutePath, opts)
    }

    //Función encargada de cargar el Modelo ONNX
    fun loadModel(): Boolean {
        return try {
            //Acá cargamos el model y lo reservamos en un buffer de memoria
            environment = OrtEnvironment.getEnvironment()
            Log.d(TAG, "ONNX (Cargando modelo desde assets): $MODEL_FILENAME")

            //Cargamos el modelo del preprocesador
            preprocessSession = loadOnnxFile(PREPROCESS_FILENAME)
            Log.d(TAG, "Preprocesador cargado: ${preprocessSession != null}")

            //Cargamos el modelo a utilizar
            session = loadOnnxFile(MODEL_FILENAME)
            Log.d(TAG, "Modelo clasificador cargado: ${session != null}")

            session != null && preprocessSession != null
        } catch (e: Exception) {
            Log.e(TAG, "Error en ONNX (cargando modelo): ${e.message}", e)
            false
        }
    }

    //Función encargada de preprocesar el audio y correr la inferencia en una llamada
    fun predictFromAudio(audioData: FloatArray): Pair<String, Float>? {
        //Paso 1: Preprocesamos el audio (audio -> spectrograma)
        val features = preprocess(audioData) ?: return null

        //Paso 2: Clasificamos por comando y conveniencia
        return predict(features)
    }

    private fun preprocess(audioData: FloatArray): FloatArray?{
        //Validamos que el modelo esté cargado
        if (preprocessSession == null || environment == null) return null
        return try{
            val inputShape = longArrayOf(1, 16000)
            val buffer = FloatBuffer.wrap(audioData)
            val tensor = OnnxTensor.createTensor(environment!!, buffer, inputShape)
            val outputs = preprocessSession?.run(mapOf("waveform" to tensor))
            val result = outputs?.get("mel_spec")?.get()

            Log.d(TAG, "Preprocess result type: ${result?.value?.javaClass?.name}")

            // Extract float array
            // Extract float array - handles multiple nesting levels
            // The preprocessor outputs (1, 1, 64, 64) which is 4096 floats
            // We need to flatten it properly
            val features = when (val v = result?.value) {
                is Array<*> -> {
                    Log.d(TAG, "Top array size: ${v.size}")
                    // v is [1] -> v[0] is [1] -> v[0][0] is [64] -> v[0][0][0] is FloatArray(64)
                    // Structure: batch[channel[rows[cols]]]
                    var current: Any? = v
                    while (current is Array<*> && current.size == 1) {
                        current = current[0]
                    }
                    // Now current should be Array<FloatArray> (64 rows of 64)
                    when (current) {
                        is Array<*> -> {
                            // Flatten 64 rows × 64 cols into 4096
                            val flat = FloatArray(64 * 64)
                            for (row in 0 until current.size) {
                                val rowData = current[row]
                                if (rowData is FloatArray) {
                                    System.arraycopy(rowData, 0, flat, row * 64, 64)
                                }
                            }
                            Log.d(TAG, "Flattened to ${flat.size} values")
                            flat
                        }
                        else -> {
                            Log.e(TAG, "Unexpected structure: ${current?.javaClass}")
                            FloatArray(64 * 64)
                        }
                    }
                }
                is FloatArray -> {
                    // Already flat
                    Log.d(TAG, "Already flat FloatArray: ${v.size}")
                    v
                }
                else -> {
                    Log.e(TAG, "Unknown type: ${v?.javaClass}")
                    FloatArray(64 * 64)
                }
            }

            Log.d(TAG, "Features: size=${features.size}, min=${features.minOrNull()}, max=${features.maxOrNull()}")

            tensor.close()
            outputs?.close()
            features
        } catch (e: Exception) {
            Log.e(TAG, "Preprocess error: ${e.message}", e)
            null
        }
    }

    /*
    * Función encargada de extraer los valores Logits del output de ONNX, manejando arrays 1D y 2D
    */
    private fun extractLogits(outputValue: Any): FloatArray? {
        return try {
            when (outputValue) {
                is Array<*> -> {
                    Log.d(TAG, "Output is Array<*>, size=${outputValue.size}")
                    val firstRow = outputValue[0]
                    when (firstRow) {
                        is FloatArray -> {
                            Log.d(TAG, "First row is FloatArray, size=${firstRow.size}")
                            firstRow
                        }
                        is Array<*> -> {
                            Log.d(TAG, "First row is Array<*>, size=${firstRow.size}")
                            FloatArray(firstRow.size) { i -> (firstRow[i] as Float) }
                        }
                        else -> {
                            Log.e(TAG, "Unknown inner type: ${firstRow?.javaClass}")
                            null
                        }
                    }
                }
                is FloatArray -> {
                    Log.d(TAG, "Output is FloatArray, size=${outputValue.size}")
                    outputValue
                }
                else -> {
                    Log.e(TAG, "Unknown output type: ${outputValue.javaClass}")
                    try {
                        if (outputValue is ai.onnxruntime.OnnxTensor) {
                            val buf = outputValue.floatBuffer
                            val arr = FloatArray(buf.remaining())
                            buf.get(arr)
                            Log.d(TAG, "Extracted via floatBuffer, size=${arr.size}")
                            arr
                        } else {
                            Log.e(TAG, "Cannot extract from type: ${outputValue.javaClass.name}")
                            null
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Fallback extraction failed: ${e2.message}")
                        null
                    }
                }
            }
        } catch (e: Exception){
            Log.e(TAG, "Error extracting logits: ${e.message}", e)
            null
        }
    }

    /**
     * Ejecuta la inferencia bajo el Espectrograma MEL
     * @param features FloatArray de tamaño 4096 (64x64 flattened)
     * @return Par conteniendo (command, confidence) o valor null si la inferencia falla
     */
    fun predict(features: FloatArray): Pair<String, Float>? {
        if (session == null || environment == null) {
            Log.e(TAG, "Session or environment is null. Model not loaded.")
            return null
        }

        return try {
            //Creamos un tensor de entrada
            //Su forma debe ser (1, 1, 64, 64)  - batch_size=1, channels=1, height=64, width=64
            val inputShape = longArrayOf(1, 1, 64, 64)
            val floatBuffer = FloatBuffer.wrap(features)
            val inputTensor = OnnxTensor.createTensor(environment!!, floatBuffer, inputShape)

            //Ejecutamos la inferencia con el espectrograma
            val inputs = mapOf(INPUT_NAME to inputTensor)
            val outputs = session?.run(inputs)

            //Obtenemos las probabilidades de salida
            val outputValue = outputs?.get(OUTPUT_NAME)?.get()
            if (outputValue == null) {
                Log.e(TAG, "Error en ONNX (Valor de salida de inferencia nulo).")
                inputTensor.close()
                outputs?.close()
                return null
            }

            //Extraemos la logística cruda
            val rawLogits = extractLogits(outputValue.value)
            if (rawLogits == null){
                //Recibimos un valor nulo
                inputTensor.close()
                outputs?.close()
                return null
            }

            //Aplicamos el SOFTMAX para suavizar la lógica y probabilidades
            val probabilities = softmax(rawLogits)

            //Checamos por todas las probabilidades
            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
            val maxProb = probabilities[maxIndex]

            //Limpiamos el tensor
            inputTensor.close()
            outputs?.close()

            //Retornamos el par esperado
            Pair(COMMANDS[maxIndex], maxProb)
        } catch (e: Exception) {
            Log.e(TAG, "Error en ONNX (inferencia de espectrograma): ${e.message}", e)
            null
        }
    }

    /**
     * Testea el modelo con un input conocido para verificar que funcione de forma correcta.
     * Permite aislar si el problema es el modelo o el procesador de audio.
     */
    fun testModelWithDummyData(): Boolean {
        if (session == null || environment == null) {
            Log.e(TAG, "Cannot test - model not loaded")
            return false
        }

        return try {
            val testFeatures = FloatArray(64 * 64) { i ->
                val row = i / 64
                val col = i % 64
                ((row - 32) * (col - 32)).toFloat() / 1000f
            }

            Log.d(TAG, "=== MODEL TEST WITH DUMMY DATA ===")
            Log.d(TAG, "Input stats: min=${testFeatures.minOrNull()}, max=${testFeatures.maxOrNull()}, mean=${testFeatures.average().toFloat()}")

            // predict() now logs everything internally
            predict(testFeatures)

            Log.d(TAG, "=== MODEL TEST COMPLETE ===")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Model test failed: ${e.message}", e)
            false
        }
    }

    //Función encargada de suavizar el valor de predicciones
    private fun softmax(logits: FloatArray): FloatArray{
        // Find max for numerical stability
        val maxLogit = logits.maxOrNull() ?: 0f

        // Compute exp(x - max) for each value
        val expValues = FloatArray(logits.size) { i ->
            exp((logits[i] - maxLogit).toDouble()).toFloat()
        }

        // Sum of all exp values
        val sumExp = expValues.sum()

        // Normalizamos el valor o vectores
        return FloatArray(logits.size) { i ->
            expValues[i] / sumExp
        }
    }

    //Función encargada de cerrar la conexión al modelo
    fun close() {
        try {
            preprocessSession?.close()
            session?.close()
            environment?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session: ${e.message}")
        }
    }
}