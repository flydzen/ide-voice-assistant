// Kotlin
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.github.flydzen.idevoiceassistant.vad.ChunkSpeechEstimator
import java.nio.FloatBuffer

class SileroChunkSpeechEstimator(
    private val sampleRate: Long = 16_000L,
    private val windowSize: Int = 512
) : ChunkSpeechEstimator {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val srTensor: OnnxTensor

    // Состояние Silero: float32[2,1,128]
    private var hiddenState: Array<Array<FloatArray>>? = null

    init {
        val modelBytes = javaClass.getResourceAsStream("/models/silero_vad.onnx")
            ?.readBytes()
            ?: error("Silero VAD model not found at /models/silero_vad.onnx")

        session = env.createSession(modelBytes)
        srTensor = OnnxTensor.createTensor(env, sampleRate) // int64 scalar
    }

    override fun getProbability(chunk: FloatArray): Float {
        if (chunk.size != windowSize) {
            // ожидается ровно 512 сэмплов, иначе считаем, что речи нет
            return 0f
        }

        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(chunk),
            longArrayOf(1, windowSize.toLong())
        )
        val stateTensor = OnnxTensor.createTensor(env, ensureState())

        return try {
            val inputs = hashMapOf<String, OnnxTensor>(
                "input" to inputTensor,
                "state" to stateTensor,
                "sr" to srTensor
            )

            session.run(inputs).use { results ->
                val outVal = results.get("output").get()
                val out = outVal.value as Array<FloatArray>
                @Suppress("UNCHECKED_CAST")
                val stateNVal = results.get("stateN").get()
                val nextState = stateNVal.value as Array<Array<FloatArray>>
                hiddenState = nextState

                // output shape: [1][1] -> берем [0][0]
                out[0][0].coerceIn(0f, 1f)
            }

        } catch (_: Throwable) {
            0f
        } finally {
            try { stateTensor.close() } catch (_: Throwable) {}
            try { inputTensor.close() } catch (_: Throwable) {}
        }
    }

    // Сброс состояния (например, между фразами)
    fun reset() {
        hiddenState = null
    }

    private fun ensureState(): Array<Array<FloatArray>> {
        val s = hiddenState
        if (s != null) return s
        val init = Array(2) { Array(1) { FloatArray(128) } }
        hiddenState = init
        return init
    }
}