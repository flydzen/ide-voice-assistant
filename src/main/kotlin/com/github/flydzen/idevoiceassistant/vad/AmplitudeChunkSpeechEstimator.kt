package com.github.flydzen.idevoiceassistant.vad

import kotlin.math.sqrt

class AmplitudeChunkSpeechEstimator(
    private val amplitudeThreshold: Float = 0.035f
) : ChunkSpeechEstimator {

    override fun isSpeech(chunk: FloatArray): Boolean {
        if (chunk.isEmpty()) return false

        return getProbability(chunk) >= amplitudeThreshold
    }

    override fun getProbability(chunk: FloatArray): Float {
        var sumSq = 0.0
        for (v in chunk) {
            val x = v.toDouble()
            sumSq += x * x
        }
        return sqrt(sumSq / chunk.size).toFloat()
    }
}