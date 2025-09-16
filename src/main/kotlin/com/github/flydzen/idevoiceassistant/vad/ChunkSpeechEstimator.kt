package com.github.flydzen.idevoiceassistant.vad


interface ChunkSpeechEstimator {
    fun isSpeech(chunk: FloatArray): Boolean

    fun getProbability(chunk: FloatArray): Float
}
