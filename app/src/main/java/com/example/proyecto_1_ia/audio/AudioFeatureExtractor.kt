package com.example.proyecto_1_ia.audio

import kotlin.math.*

class AudioFeatureExtractor {
    //Esta clase se encargará de extraer el audio

    //STFT (Shortest Time Fourier Transformation)

    companion object {
        //private const val TAG = "AudioFeatureExtractor"
        const val SAMPLE_RATE = 16000
        const val N_MELS = 64       //Tamaño de InputSize (de importancia para el espectrograma)

        //Son la cantidad de samples que se analizan a la vez
        const val N_FFT = 1024      // Debe ser divisible entre 2 o el square root de 2 para FFT

        //Que tanto se desplaza de la ventana, 512 significa que hay un 50% de overlap (por ser la mitad de 1024)
        const val HOP_LENGTH = 512  //Largo del
        const val TARGET_SIZE = 64  //Tamaño del target (de importancia para el espectrograma)

        //Hacemos precomputacion de pantalla espectograma
        //Esto suaviza los bordes de cada frame para evitar artefactos (es como fadein/fadeout)
        private val hammingWindow: FloatArray by lazy {
            FloatArray(N_FFT) { i ->
                (0.54 - 0.46 * cos(2.0 * PI * i / (N_FFT - 1))).toFloat()
            }
        }

        //====== Precomputación de filtros MEL ======//
        private fun createMelFilterBanks(): Array<FloatArray> {
            //Creamos los bins
            val numFreqBins = N_FFT / 2 + 1 //513 bins de frecuencia

            val melMin = 2595.0 * log10(1.0 + 0.0 / 700.0)
            val melMax = 2595.0 * log10(1.0 + (SAMPLE_RATE / 2.0) / 700.0)
            val melPoints = DoubleArray(N_MELS + 2) { i ->
                melMin + (melMax - melMin) * i / (N_MELS + 1)
            }

            //Convertimos a valores de hercios (hz) y luego a puntos
            val hzPoints = melPoints.map { mel ->
                700.0 * (10.0.pow(mel / 2595.0) - 1.0)
            }
            val binPoints = hzPoints.map { hz ->
                (hz * (N_FFT + 1) / SAMPLE_RATE).toInt().coerceIn(0, numFreqBins - 1)
            }

            //Finalmente; creamos los filtros y los aplicamos
            val filters = Array(N_MELS) { FloatArray(numFreqBins) }
            for (mel in 0 until N_MELS) {
                for (bin in binPoints[mel] until binPoints[mel + 2]) {
                    if (bin < numFreqBins) {
                        filters[mel][bin] = if (bin < binPoints[mel + 1]) {
                            (bin - binPoints[mel]).toFloat() / (binPoints[mel + 1] - binPoints[mel]).toFloat()
                        } else {
                            (binPoints[mel + 2] - bin).toFloat() / (binPoints[mel + 2] - binPoints[mel + 1]).toFloat()
                        }
                    }
                }
            }
            return filters
        }
    }

    //Aplicamos pre-computación al FFT para que solo se ejecute una vez
    private val twiddleCos: FloatArray by lazy {
        FloatArray(N_FFT / 2) { k ->
            cos(-2.0 * PI * k / N_FFT).toFloat()
        }
    }
    private val twiddleSin: FloatArray by lazy {
        FloatArray(N_FFT / 2) { k ->
            sin(-2.0 * PI * k / N_FFT).toFloat()
        }
    }

    // Pre-computed Mel filter banks (computed ONCE)
    private val melFilters: Array<FloatArray> by lazy {
        createMelFilterBanks()
    }

    //Función encargada de convertir los samples de audio en espectrogramas
    fun audioToMelSpectogram(audioData: FloatArray): FloatArray {
        try {
            // 1. Primero computamos la magnitud de la onda STFT
            val stftMagnitude = computeSTFT(audioData)


            // 2. Aplicamos filtros MEL y convertimos a decibeles
            val melSpec = applyMelFiltersAndDb(stftMagnitude)

            // 3. Aplicamos un cambio de tamaño al espectrograma a su tamaño 64x64
            val result = resizeToTarget(melSpec)

            return result
        } catch (e: Exception) {
            //A niveles de probabilidad de espectrograma, retornamos 0
            return FloatArray(TARGET_SIZE * TARGET_SIZE)
        }
    }

    //Función (OPTIMIZADA) encargada de computar o medir el nivel de magnitud de una ola en espectrograma
    fun computeSTFT(audioData: FloatArray): Array<FloatArray> {
        //Calculamos número de frames y bins que tiene nuestro data de sonido
        val numFrames = (audioData.size - N_FFT) / HOP_LENGTH + 1
        val numFreqBins = N_FFT / 2 + 1
        //En base a ese, creamos un array que contiene un array de flotantes (filas numFrames, columnas numFreqBins)
        val stft = Array(numFrames) { FloatArray(numFreqBins) }

        //Reusamos los arrays de FFT (declarados antes del LOOP)
        val real = FloatArray(N_FFT)
        val imag = FloatArray(N_FFT)

        //Iteramos por todos los frames para computar el nivel de magnitud
        for (frame in 0 until numFrames) {
            val start = frame * HOP_LENGTH

            //Aplicamos el window de movimiento o suavizado
            for (i in 0 until N_FFT) {
                if (start + i < audioData.size) {
                    real[i] = audioData[start + i] * hammingWindow[i]
                } else {
                    real[i] = 0f
                }
                imag[i] = 0f
            }

            //Permutación de bit reverso
            var j = 0
            for (i in 0 until N_FFT) {
                if (i < j) {
                    real[i] = real[j].also { real[j] = real[i] }
                    imag[i] = imag[j].also { imag[j] = imag[i] }
                }
                var k = N_FFT shr 1
                while (k in 1..j) {
                    j -= k
                    k = k shr 1
                }
                j += k
            }

            //Aplicamos computación de mariposa al FFT
            var step = 2
            while (step <= N_FFT) {
                val halfStep = step / 2
                for (groupStart in 0 until N_FFT step step) {
                    for (pair in 0 until halfStep) {
                        val idx1 = groupStart + pair
                        val idx2 = idx1 + halfStep

                        val twiddleIndex = pair * (N_FFT / step)
                        val wr = twiddleCos[twiddleIndex]
                        val wi = twiddleSin[twiddleIndex]

                        val tr = real[idx2] * wr - imag[idx2] * wi
                        val ti = real[idx2] * wi + imag[idx2] * wr

                        real[idx2] = real[idx1] - tr
                        imag[idx2] = imag[idx1] - ti
                        real[idx1] += tr
                        imag[idx1] += ti
                    }
                }
                step = step shl 1
            }

            //Finalmente extraemos magnitud para el primer N_FFT/2 + 1 bins o el array STFT
            for (k in 0 until numFreqBins) {
                stft[frame][k] = sqrt(real[k] * real[k] + imag[k] * imag[k])
            }
        }
        return stft
    }

    /*
    * Función que combina aplicarle cuadrado a las magnitudes, aplicar filtros y convertir a decibeles
    * Así nos evitamos crear un powerSpec Array de forma inmediata
    */
    fun applyMelFiltersAndDb(stft: Array<FloatArray>): Array<FloatArray> {
        //De igual manera ocupamos agarrar los frames y los bins de nuestro audio
        val numFrames = stft.size  //Cuantos pedazos o frames obtenemos (16000 - 1024) / 512 + 1
        val numFreqBins =
            stft[0].size //Ya que es matriz cuadrada, podemos agarrar el tamaño de cualquiera
        val melSpec = Array(numFrames) { FloatArray(N_MELS) }

        //Empezaremos a iterar sobre nuestro array o matriz
        for (frame in 0 until numFrames) {
            val frameData = stft[frame]         //Guardamos los datos en una variable temporal
            for (mel in 0 until N_MELS) {
                var sum = 0f
                //Creamos un filtro
                val filter = melFilters[mel]
                //Aplicamos un valor al cuadrado y aplicamos el filtro en un solo paso
                for (bin in 0 until numFreqBins) {
                    sum += frameData[bin] * frameData[bin] * filter[bin]
                }
                //Aplicamos conversión a decibeles junto a un valor de epsilon para evitar log(0)
                melSpec[frame][mel] = 10f * log10(max(sum, 1e-10f))
            }
        }
        return melSpec
    }

    //Función encargada de tomar los datos en decibel de un espectrograma y finalmente aplicarles cambio de tamaño
    //de modo que ya formen el aspecto del espectrograma deseado
    private fun resizeToTarget(melSpecDb: Array<FloatArray>): FloatArray {
        //Acá básicamente colocamos el valor de filas y columnas originales
        val originalRows = melSpecDb.size
        val originalCols = if (originalRows > 0) melSpecDb[0].size else N_MELS
        val result = FloatArray(TARGET_SIZE * TARGET_SIZE)

        //Calculamos de forma previa el valor de scale
        val rowScale = (originalRows - 1).toFloat() / (TARGET_SIZE - 1).toFloat()
        val colScale = (originalCols - 1).toFloat() / (TARGET_SIZE - 1).toFloat()

        //Interpolamos a un tamaño del 64x64
        for (i in 0 until TARGET_SIZE) {
            val srcI = (i * rowScale).toInt().coerceIn(0, originalRows - 1)
            val row = melSpecDb[srcI]
            val offset = i * TARGET_SIZE
            for (j in 0 until TARGET_SIZE) {
                val srcJ = (j * colScale).toInt().coerceIn(0, originalCols - 1)
                result[offset + j] = row[srcJ]
            }
        }
        return result
    }
}

