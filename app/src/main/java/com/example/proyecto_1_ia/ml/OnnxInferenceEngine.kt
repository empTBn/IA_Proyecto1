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
    private var environment: OrtEnvironment? = null

    //Creamos un objeto companion para manejar la solicitud de comandos
    companion object {
        private const val TAG = "OnnxInferenceEngine"  //Colocamos un TAG para manejar errores
        const val MODEL_FILENAME = "model.onnx"     //Nombre del archivo de modelos
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

    //Función encargada de cargar el Modelo ONNX
    fun loadModel(): Boolean {
        return try {
            //Acá cargamos el model y lo reservamos en un buffer de memoria
            environment = OrtEnvironment.getEnvironment()
            Log.d(TAG, "ONNX (Cargando modelo desde assets): $MODEL_FILENAME")

            //Copiamos archivos de assets al storage interno
            val modelDir = context.filesDir
            val modelFile = File(modelDir, MODEL_FILENAME)
            val dataFile = File(modelDir, "$MODEL_FILENAME.data")

            //Ahora sí copiamos el modelo.onnx de forma correcta
            context.assets.open(MODEL_FILENAME).use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Modelo copiado: ${modelFile.absolutePath} (${modelFile.length() / 1024}KB)")

            //Copiamos model.onnx.data
            try {
                context.assets.open("$MODEL_FILENAME.data").use { input ->
                    dataFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.d(TAG, "Datos externos copiados: ${dataFile.absolutePath} (${dataFile.length() / 1024}KB)")
            } catch (e: Exception) {
                Log.w(TAG, "Error en ONNX (No se encontró archivo de datos externos)")
            }

            //Acá entonces, creamos una sesión de Ort, la optimizamos (la utilizaremos para crear nuestra sesion ONNX)
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT) //Optimizamos en Android
            sessionOptions.addCPU(true)  //Indicamos que use CPU en esto

            //Finalmente, terminamos de cargar el modelo, creando una sesión con el buffer y las opciones de ORT
            session = environment?.createSession(modelFile.absolutePath, sessionOptions)
            Log.d(TAG, "ONNX (Modelo cargado exitosamente!)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error en ONNX (cargando modelo): ${e.message}", e)
            false
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
            session?.close()
            environment?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session: ${e.message}")
        }
    }
}