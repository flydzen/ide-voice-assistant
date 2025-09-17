package com.github.flydzen.idevoiceassistant.vad

import kotlin.math.sqrt

class AmplitudeChunkSpeechEstimator(
    private val gain: Float = 40f,       // усиливает RMS → вероятность
    private val attack: Float = 0.7f,    // скорость роста (0..1)
    private val release: Float = 0.05f   // скорость спада (0..1)
) : ChunkSpeechEstimator {

    private var smoothedProb: Float = 0f

    override fun getProbability(chunk: FloatArray): Float {
        if (chunk.isEmpty()) return smoothedProb

        var sumSq = 0.0
        for (v in chunk) {
            val x = v.toDouble()
            sumSq += x * x
        }
        val rms = sqrt(sumSq / chunk.size).toFloat()

        val rawProb = (rms * gain).coerceIn(0f, 1f)

        val alpha = if (rawProb > smoothedProb) attack else release
        smoothedProb += alpha * (rawProb - smoothedProb)
        // smoothedProb = smoothedProb * (1 - alpha) + alpha * rawProb

        return smoothedProb
    }
}