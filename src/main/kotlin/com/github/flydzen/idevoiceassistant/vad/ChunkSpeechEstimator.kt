package com.github.flydzen.idevoiceassistant.vad


fun interface ChunkSpeechEstimator {
    fun isSpeech(chunk: FloatArray): Boolean
}
