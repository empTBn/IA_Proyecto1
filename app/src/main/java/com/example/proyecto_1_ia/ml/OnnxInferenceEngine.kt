package com.example.proyecto_1_ia.ml


import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession //Librería completa de onnx
import android.content.Context
import android.util.Log
import java.nio.FloatBuffer
import java.io.File

class OnnxInferenceEngine(private val context: Context){
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
    fun loadModel(): Boolean{
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
                    dataFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Datos externos copiados: ${dataFile.absolutePath} (${dataFile.length() / 1024}KB)")
            } catch (e: Exception){
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
        } catch (e: Exception){
            Log.e(TAG, "Error en ONNX (cargando modelo): ${e.message}", e)
            false
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
            val inputTensor = OnnxTensor.createTensor(
                environment!!,
                floatBuffer,
                inputShape
            )

            //Ejecutamos la inferencia con el espectrograma
            val inputs = mapOf(INPUT_NAME to inputTensor)
            val outputs = session?.run(inputs)

            //Obtenemos las probabilidades de salida
            val outputValue = outputs?.get(OUTPUT_NAME)?.get()
            if (outputValue == null){
                Log.e(TAG, "Error en ONNX (Valor de salida de inferencia nulo).")
                inputTensor.close()
                outputs?.close()
                return null
            }

            //Manejamos las probabilidades
            // Handle different output types
            val probabilities: FloatArray = when (val value = outputValue.value) {
                is Array<*> -> {
                    // Output is float[][] - extract first row
                    Log.d(TAG, "Output is 2D array, extracting first row")
                    val firstRow = value[0]
                    when (firstRow) {
                        is FloatArray -> firstRow
                        is Array<*> -> {
                            FloatArray(firstRow.size) { i -> (firstRow[i] as Float) }
                        }
                        else -> {
                            Log.e(TAG, "Unexpected inner type: ${firstRow?.javaClass}")
                            FloatArray(COMMANDS.size)
                        }
                    }
                }
                is FloatArray -> {
                    // Output is already float[] (1D)
                    Log.d(TAG, "Output is 1D array")
                    value
                }
                else -> {
                    Log.e(TAG, "Unexpected output type: ${value?.javaClass}")
                    FloatArray(COMMANDS.size)
                }
            }

            // Find highest probability
            var maxIndex = 0
            var maxProb = probabilities[0]
            for (i in probabilities.indices) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxIndex = i
                }
            }

            //Log.d(TAG, "Predicted: ${COMMANDS[maxIndex]} with confidence: $maxProb")
            //Log.d(TAG, "All probabilities: ${probabilities.joinToString { "%.3f".format(it) }}")

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

    //Función para debugging con todas las probabilidades
    fun predictWithAllProbabilities(features: FloatArray): Map<String, Float>? {
        //De una vez validamos existencia de sesión y entorno
        if (session == null || environment == null) return null

        return try {
            //Hacemos casi lo mismo que en el predict normal
            val inputShape = longArrayOf(1, 1, 64, 64)
            val floatBuffer = FloatBuffer.wrap(features)
            val inputTensor = OnnxTensor.createTensor(environment!!,floatBuffer, inputShape)

            val inputs = mapOf(INPUT_NAME to inputTensor)
            val outputs = session?.run(inputs)

            val outputValue = outputs?.get(OUTPUT_NAME)?.get()
            if (outputValue == null){
                Log.e(TAG, "Error en ONNX (Valor de salida de inferencia nulo).")
                inputTensor.close()
                outputs?.close()
                return null
            }
            val probabilities = outputValue.value as FloatArray

            //Ahora en vez de agarrar la de mayor valor, las pasamos a un Map
            val result = mutableMapOf<String, Float>()
            for (i in COMMANDS.indices){
                result[COMMANDS[i]] = probabilities[i]
            }

            //Cerramos los tensores
            inputTensor.close()
            outputs?.close()

            result
        } catch (e: Exception){
            Log.e(TAG, "Error en ONNX DEBUG FUNCTION (error obteniendo probabilidades): ${e.message}", e)
            null
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