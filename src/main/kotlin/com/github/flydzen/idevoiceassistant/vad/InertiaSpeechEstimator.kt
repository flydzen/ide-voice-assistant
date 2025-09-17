// Kotlin
package com.github.flydzen.idevoiceassistant.vad

class InertiaSpeechEstimator(
    private val delegate: ChunkSpeechEstimator,
    private val attack: Float = 0.85f,   // скорость роста (0..1)
    private val release: Float = 0.1f, // скорость спада (0..1)
    private val gain: Float = 1.5f,     // дополнительное усиление (опционально)
    private val floor: Float = 0f,      // нижняя граница после усиления
    private val ceil: Float = 1f        // верхняя граница после усиления
) : ChunkSpeechEstimator {

    private var smoothed: Float = 0f

    override fun getProbability(chunk: FloatArray): Float {
        val raw = (delegate.getProbability(chunk) * gain).coerceIn(floor, ceil)
        val alpha = if (raw > smoothed) attack else release
        smoothed += alpha * (raw - smoothed)
        return smoothed
    }

    fun reset(initial: Float = 0f) {
        smoothed = initial.coerceIn(floor, ceil)
    }
}