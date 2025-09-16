package com.github.flydzen.idevoiceassistant.vad


interface ChunkSpeechEstimator {
    fun getProbability(chunk: FloatArray): Float
}
