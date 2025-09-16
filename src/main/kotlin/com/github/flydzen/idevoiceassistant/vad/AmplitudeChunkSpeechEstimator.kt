package com.github.flydzen.idevoiceassistant.vad

import kotlin.math.sqrt

class AmplitudeChunkSpeechEstimator: ChunkSpeechEstimator {
    override fun getProbability(chunk: FloatArray): Float {
        var sumSq = 0.0
        for (v in chunk) {
            val x = v.toDouble()
            sumSq += x * x
        }
        return sqrt(sumSq / chunk.size).toFloat()
    }
}