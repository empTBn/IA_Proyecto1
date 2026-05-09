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
        const val F_MIN = 0.0
        const val F_MAX = 8000.0  // SAMPLE_RATE / 2

        //Hacemos precomputacion de pantalla espectograma
        //Esto suaviza los bordes de cada frame para evitar artefactos (es como fadein/fadeout)
        //El modelo utiliza TorchAudio, quien usa HannWindow, no HammingWindow
        private val hannWindow: FloatArray by lazy {
            FloatArray(N_FFT) { i ->
                (0.5 * (1.0 - cos(2.0 * PI * i / (N_FFT - 1)))).toFloat()
            }
        }

        //====== Precomputación de filtros MEL ======//
        private val melFilters: Array<FloatArray> by lazy {
            createMelFilterBanksTorchAudio()
        }

        //El sistema utilizado en TorchAudio para crear los filtros es distinto al que teníamos previamente

        private fun createMelFilterBanksTorchAudio(): Array<FloatArray> {
            //Creamos los bins
            val numFreqBins = N_FFT / 2 + 1 //513 bins de frecuencia

            //Usamos la fórmula de Slaney para convertir los HZ a Mel (default de torchaudio)
            fun hzToMel(hz: Double): Double{
                val fMinMel = 2595.0 * log10(1.0 + F_MIN / 700.0)
                val fMaxMel = 2595.0 * log10(1.0 + F_MAX / 700.0)
                val mel = 2595.0 * log10(1.0 + hz / 700.0)
                // Normalize to be between 0 and 1 (Slaney norm)
                return (mel - fMinMel) / (fMaxMel - fMinMel) * N_MELS
            }
            fun melToHz(mel: Double): Double{
                val fMinMel = 2595.0 * log10(1.0 + F_MIN / 700.0)
                val fMaxMel = 2595.0 * log10(1.0 + F_MAX / 700.0)
                val melUnnormalized = mel / N_MELS * (fMaxMel - fMinMel) + fMinMel
                return 700.0 * (10.0.pow(melUnnormalized / 2595.0) - 1.0)
            }

            //Creamos puntos de Mel espaciados de forma equitativa
            val melPoints = DoubleArray(N_MELS + 2) { i -> i.toDouble() }
            //Convertimos a valores de hercios (hz) y luego a puntos
            val hzPoints = melPoints.map { melToHz(it) }
            val binPoints = hzPoints.map { hz ->
                (hz * (N_FFT + 2) / SAMPLE_RATE).toInt().coerceIn(0, numFreqBins - 1)
            }

            //Finalmente; creamos los filtros y los aplicamos
            val filters = Array(N_MELS) { FloatArray(numFreqBins) }
            for (mel in 0 until N_MELS) {
                val startBin = binPoints[mel]
                val centerBin = binPoints[mel + 1]
                val endBin = binPoints[mel + 2]

                //Subida creciente (Rising slope)
                if (centerBin > startBin) {
                    for (bin in startBin until centerBin) {
                        if (bin < numFreqBins) {
                            filters[mel][bin] = (bin - startBin).toFloat() / (centerBin - startBin).toFloat()
                        }
                    }
                }
                //Bajada descendente (Falling slope)
                if (endBin > centerBin) {
                    for (bin in centerBin until endBin) {
                        if (bin < numFreqBins) {
                            filters[mel][bin] = (endBin - bin).toFloat() / (endBin - centerBin).toFloat()
                        }
                    }
                }
            }

            //Aplicamos normalización a los filtros (utilizando la normalización de Slaney)
            for (mel in 0 until N_MELS) {
                val sum = filters[mel].sum()
                if (sum > 0f) {
                    for (bin in filters[mel].indices) {
                        filters[mel][bin] /= sum
                    }
                }
            }
            return filters
        }
    }

    //Aplicamos pre-computación al FFT para que solo se ejecute una vez
    private val twiddleCos: FloatArray by lazy {
        FloatArray(N_FFT / 2) { k -> cos(-2.0 * PI * k / N_FFT).toFloat()
        }
    }
    private val twiddleSin: FloatArray by lazy {
        FloatArray(N_FFT / 2) { k -> sin(-2.0 * PI * k / N_FFT).toFloat()
        }
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
                    real[i] = audioData[start + i] * hannWindow[i]
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
                stft[frame][k] = (real[k] * real[k] + imag[k] * imag[k])  // Power spectrum
            }
        }
        return stft
    }

    /*
    * Función que combina aplicarle cuadrado a las magnitudes, aplicar filtros y convertir a decibeles
    * Así nos evitamos crear un powerSpec Array de forma inmediata
    */
    private fun applyMelFiltersAndDb(stft: Array<FloatArray>): Array<FloatArray> {
        //De igual manera ocupamos agarrar los frames y los bins de nuestro audio
        val numFrames = stft.size  //Cuantos pedazos o frames obtenemos (16000 - 1024) / 512 + 1
        val numFreqBins = stft[0].size //Ya que es matriz cuadrada, podemos agarrar el tamaño de cualquiera
        val melSpec = Array(numFrames) { FloatArray(N_MELS) }

        //Empezaremos a iterar sobre nuestro array o matriz
        for (frame in 0 until numFrames) {
            val frameData = stft[frame]         //Guardamos los datos en una variable temporal
            for (mel in 0 until N_MELS) {
                var sum = 0f
                //Creamos un filtro
                val filter = melFilters[mel]
                //Aplicamos el filtro ya que previamente aplicamos el power
                for (bin in 0 until numFreqBins) {
                    sum += frameData[bin] * filter[bin]
                }
                //Aplicamos conversión a decibeles junto a un valor de epsilon para evitar log(0)
                //torch audio utiliza un valor top de top_db = 80.0, lo cual deja el valor de máximo a 80.0db
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
        val originalCols = melSpecDb[0].size
        val result = FloatArray(TARGET_SIZE * TARGET_SIZE)

        //Requerimos aplicar Bilinear interpolation, tal como usa torch audio
        //Interpolamos a un tamaño del 64x64
        for (i in 0 until TARGET_SIZE) {
            for (j in 0 until TARGET_SIZE) {
                val srcI = i.toFloat() * (originalRows - 1) / (TARGET_SIZE - 1)
                val srcJ = j.toFloat() * (originalCols - 1) / (TARGET_SIZE - 1)

                val i0 = srcI.toInt().coerceIn(0, originalRows - 1)
                val i1 = (i0 + 1).coerceIn(0, originalRows - 1)
                val j0 = srcJ.toInt().coerceIn(0, originalCols - 1)
                val j1 = (j0 + 1).coerceIn(0, originalCols - 1)

                val wi = srcI - i0
                val wj = srcJ - j0

                val v00 = melSpecDb[i0][j0]
                val v01 = melSpecDb[i0][j1]
                val v10 = melSpecDb[i1][j0]
                val v11 = melSpecDb[i1][j1]

                val top = v00 * (1 - wj) + v01 * wj
                val bottom = v10 * (1 - wj) + v11 * wj

                result[i * TARGET_SIZE + j] = top * (1 - wi) + bottom * wi
            }
        }
        return result
    }

}

