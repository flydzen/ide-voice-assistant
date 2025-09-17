package com.github.flydzen.idevoiceassistant.vad

import kotlin.math.sqrt

class AmplitudeChunkSpeechEstimator: ChunkSpeechEstimator {

    private var smoothedProb: Float = 0f

    override fun getProbability(chunk: FloatArray): Float {
        if (chunk.isEmpty()) return smoothedProb

        var sumSq = 0.0
        for (v in chunk) {
            val x = v.toDouble()
            sumSq += x * x
        }
        return sqrt(sumSq / chunk.size).toFloat()
    }
}