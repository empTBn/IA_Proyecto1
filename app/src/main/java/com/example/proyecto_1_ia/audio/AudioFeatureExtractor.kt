package com.example.proyecto_1_ia.audio

import android.media.AudioFormat
import android.util.Log
import java.sql.Date
import kotlin.math.*

class AudioFeatureExtractor {
    //Esta clase se encargará de extraer el audio

    companion object {
        private const val TAG = "AudioFeatureExtractor"
        const val SAMPLE_RATE = 16000
        const val N_MELS = 64       //Tamaño de InputSize (de importancia para el espectrograma)
        const val N_FFT = 1024
        const val HOP_LENGTH = 512  //Largo del
        const val TARGET_SIZE = 64  //Tamaño del target (de importancia para el espectrograma)

        //Hacemos precomputacion de pantalla espectograma
        private val hammingWindow: FloatArray by lazy {
            FloatArray(N_FFT) { i ->
                (0.54 - 0.46 * cos(2.0 * PI * i / (N_FFT - 1))).toFloat()
            }
        }
    }

    //Función encargada de convertir los samples de audio en espectrogramas
    fun audioToMelSpectogram(audioData: FloatArray): FloatArray {
        try {
            // 1. Primero computamos la magnitud de la onda STFT
            val stftMagnitude = computeSTFT(audioData)

            // 2. Convertimos y medimos el Spectrum de poder
            val powerSpec = Array(stftMagnitude.size) { frame ->
                FloatArray(stftMagnitude[frame].size) { bin ->
                    stftMagnitude[frame][bin] * stftMagnitude[frame][bin]
                }
            }

            // 3. Aplicamos un filtrado de bancos de sonido
            val melSpec = applyMelFilters(powerSpec)

            // 4. Convertimos el valor de filtrado de sonido a Decibeles
            val melSpecDb = amplitudeToDb(melSpec)

            // 5. Aplicamos un cambio de tamaño al espectrograma a su tamaño 64x64
            return resizeToTarget(melSpecDb)
        } catch (e: Exception){
            //A niveles de probabilidad de espectrograma, retornamos 0
            Log.e(TAG, "Error en AudioFeatureExtractor (extrayendo características a espectrograma): ${e.message}")
            return FloatArray(TARGET_SIZE * TARGET_SIZE)
        }
    }

    //Función encargada de computar o medir el nivel de magnitud de una ola en espectrograma
    fun computeSTFT(audioData: FloatArray): Array<FloatArray> {
        //Calculamos número de frames y bins que tiene nuestro data de sonido
        val numFrames = (audioData.size - N_FFT) / HOP_LENGTH + 1
        val numFreqBins = N_FFT / 2 + 1
        //En base a ese, creamos un array que contiene un array de flotantes (filas numFrames, columnas numFreqBins)
        val stft = Array(numFrames) { FloatArray(numFreqBins) }

        //Iteramos por todos los frames para computar el nivel de magnitud
        for (frame in 0 until numFrames){
            val start = frame * HOP_LENGTH

            //Extraemos el frame y aplicamos el límite previo de nuestro hamming window
            val real = FloatArray(N_FFT)
            val imag = FloatArray(N_FFT)

            for (i in 0 until N_FFT){
                if (start + i < audioData.size){
                    real[i] = audioData[start + i]  * hammingWindow[i]
                }
                imag[i] = 0f
            }

            //Computamos el valor de DFT para cada bin de frecuencia
            for (k in 0 until numFreqBins){
                var sumReal = 0f
                var sumImag = 0f

                for (n in 0 until N_FFT){
                    //Calculamos el ángulo de crecimiento
                    val angle = -2.0 * PI * k * n / N_FFT
                    sumReal += real[n] * cos(angle).toFloat() - imag[n] * sin(angle).toFloat()
                    sumImag += real[n] * sin(angle).toFloat() + imag[n] * cos(angle).toFloat()
                }

                //Actualizamos el valor de magnitud usando raíz cuadrada
                //sqrt(sumReal^2 + sumImag^2) / 2
                stft[frame][k] = sqrt(sumReal * sumReal + sumImag * sumImag) / N_FFT
            }
        }

        return stft
    }

    //Función encargada de filtrar los bancos de ruido en la imagen del espectograma
    fun applyMelFilters(powerSpec: Array<FloatArray>): Array<FloatArray>{
        //De igual manera ocupamos agarrar los frames y los bins de nuestro audio
        val numFrames = powerSpec.size
        val numFreqBins = powerSpec[0].size //Ya que es un matriz cuadrado, podemos agarrar el tamaño de cualquiera
        val melSpec = Array(numFrames) { FloatArray(N_MELS) }

        //Para el filtrador, si usamos el número de frames, pero usamos nuestro valor de N_MELS para terminar dicha matriz
        //Creamos los bancos filtradores
        val melFilters = createMelFilterBanks(numFreqBins)

        ///Una vez creados los filtros, vamos a aplicarlos
        for (frame in 0 until numFrames){
            for (mel in 0 until N_MELS){
                var sum = 0f
                for (bin in 0 until numFreqBins){
                    sum += powerSpec[frame][bin] * melFilters[mel][bin]
                }
                //Agregamos un valor de epsilon para evitar log(0)
                melSpec[frame][mel] = max(sum, 1e-10f)
            }
        }
        return melSpec
    }
    //Función auxiliar que permite la creación de bancos de filtración
    fun createMelFilterBanks(numFreqBins: Int): Array<FloatArray>{
        //Creamos los bancos de filtración, primero determinamos los límites
        val melMin = hzToMel(0.0)
        val melMax = hzToMel(SAMPLE_RATE / 2.0)
        val melPoints = DoubleArray(N_MELS + 2)

        //Iteramos por los índices de los puntos del espectrograma
        for (i in melPoints.indices){
            melPoints[i] = melMin + (melMax - melMin) * i / (N_MELS + 1)
        }

        //Creamos un mapa de puntos del espectrograma, pero esta vez estos en valor de hz
        val hzPoints = melPoints.map { melToHz(it) }
        //En base al mapa, creamos nuestro mapa de puntos para los bins
        val binPoints = hzPoints.map {
            (it * (N_FFT + 1) / SAMPLE_RATE).toInt().coerceIn(0, numFreqBins - 1)
        }

        //Finalmente; creamos los filtros y los aplicamos
        val filters = Array(N_MELS) { FloatArray(numFreqBins) }
        for (mel in 0 until N_MELS){
            for (bin in binPoints[mel] until binPoints[mel + 2]){
                if (bin < numFreqBins) {
                    //Si el valor del bin es inferior al valor de nuestro parámetro, aplicamos filtro
                    filters[mel][bin] = if (bin < binPoints[mel+1]){
                        (bin - binPoints[mel]).toFloat() / (binPoints[mel + 1] - binPoints[mel]).toFloat()
                    } else {
                        (binPoints[mel + 2] - bin).toFloat() / (binPoints[mel + 2] - binPoints[mel + 1]).toFloat()
                    }
                }
            }
        }

        return filters
    }

    //Función encargada de tomar los datos en decibel de un espectrograma y finalmente aplicarles cambio de tamaño
    //de modo que ya formen el aspecto del espectrograma deseado
    fun resizeToTarget(melSpecDb: Array<FloatArray>): FloatArray {
        //Acá básicamente colocamos el valor de filas y columnas originales
        val originalRows = melSpecDb.size
        val originalCols = if (originalRows > 0) melSpecDb[0].size else N_MELS
        val result = FloatArray(TARGET_SIZE * TARGET_SIZE)

        //Interpolamos a un tamaño del 64x64
        for (i in 0 until TARGET_SIZE) {
            for (j in 0 until TARGET_SIZE) {
                val srcI = (i.toFloat() * (originalRows - 1) / (TARGET_SIZE - 1)).toInt()
                val srcJ = (j.toFloat() * (originalCols - 1) / (TARGET_SIZE - 1)).toInt()

                val safeSrcI = srcI.coerceIn(0, originalRows - 1)
                val safeSrcJ = srcJ.coerceIn(0, originalCols - 1)

                result[i * TARGET_SIZE + j] = melSpecDb[safeSrcI][safeSrcJ]
            }
        }

        return result

    }


    //========= FUNCIONES MATEMÁTICAS =========//
    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

    //Función encargada de convertir audioData de su valor previo a su valor en decibeles
    private fun amplitudeToDb(melSpec: Array<FloatArray>): Array<FloatArray>{
        //Nos encargamos de convertir los valores de amplitud a decibeles
        return Array(melSpec.size) { frame ->
            FloatArray(melSpec[frame].size) { mel ->
                10f * log10(melSpec[frame][mel])
            }
        }
    }

}