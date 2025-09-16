package com.github.flydzen.idevoiceassistant.vad

import kotlin.math.sqrt

class AmplitudeChunkSpeechEstimator(
    private val amplitudeThreshold: Float = 0.035f
) : ChunkSpeechEstimator {

    override fun isSpeech(chunk: FloatArray): Boolean {
        if (chunk.isEmpty()) return false
        var sumSq = 0.0
        for (v in chunk) {
            val x = v.toDouble()
            sumSq += x * x
        }
        val rms = sqrt(sumSq / chunk.size)
        return rms >= amplitudeThreshold
    }
}