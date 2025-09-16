//package com.github.flydzen.idevoiceassistant.vad
//
//
//import ai.onnxruntime.OrtEnvironment
//import ai.onnxruntime.OnnxTensor
//import ai.onnxruntime.OrtSession
//import com.intellij.openapi.components.Service
//import com.intellij.openapi.components.service
//import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.Flow
//import org.slf4j.LoggerFactory
//import java.nio.FloatBuffer
//import java.io.InputStream
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//
//@Service(Service.Level.APP)
//class VadService {
//
//    private val env = OrtEnvironment.getEnvironment()
//    private val session: OrtSession
//
//    private val windowSize = 512
//
//    // Silero VAD expects single "state" tensor: float32[2, batch, 128]
//    // We'll keep it as a 3D float array [2][1][128] and build an OnnxTensor on each call.
//    private var hiddenState: Array<Array<FloatArray>>? = null
//    private val srTensor: OnnxTensor
//
//    private val buffer = FloatArray(windowSize)
//    private var bufferIndex = 0
//    private var speechActive = false
//
//    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//    private val log = LoggerFactory.getLogger(VadService::class.java)
//
//    private val frameBuffer = FloatArray(512)
//    private var frameFill = 0
//
//    init {
//        val modelBytes = javaClass.getResourceAsStream("/models/silero_vad.onnx")
//            ?.readBytes()
//            ?: error("VAD model not found in resources!")
//        session = env.createSession(modelBytes)
//        srTensor = OnnxTensor.createTensor(env, 16000L) // int64 scalar
//    }
//
//    private fun onByteReceived(b: Byte) {
//        frameBuffer[frameFill++] = b / 128f
//
//        // при заполнении фрейма считаем показатели
//        if (frameFill >= windowSize) {
//            processStreamData(frameBuffer)
//            frameFill = 0
//        }
//    }
//
//    fun startListening(samples: Flow<Byte?>) {
//        scope.launch {
//            try {
//                samples.collect { byte ->
//                    if (!isActive) return@collect
//                    if (byte == null) return@collect
//                    onByteReceived(byte)
//                }
//            } catch (t: Throwable) {
//                log.warn("VAD flow collection stopped with error", t)
//            } finally {
//                reset()
//            }
//        }
//    }
//
//    fun startListening(inputStream: InputStream) {
//        scope.launch {
//            val frameSize = 320 // 20 ms frames for 16kHz
//            val byteBuffer = ByteArray(frameSize * 2)
//
//            while (coroutineContext.isActive) {
//                val read = inputStream.read(byteBuffer)
//                if (read == -1) break
//                if (read < byteBuffer.size) continue // waiting for the frame to accumulate
//
//                // Conversion PCM16 -> FloatArray [-1,1]
//                val shortArray = ShortArray(frameSize)
//                ByteBuffer.wrap(byteBuffer)
//                    .order(ByteOrder.LITTLE_ENDIAN)
//                    .asShortBuffer()
//                    .get(shortArray)
//                val floatArray = FloatArray(frameSize) { i -> shortArray[i] / 32768.0f }
//
//                processStreamData(floatArray)
//            }
//        }
//    }
//
//    fun stopListening() {
//        scope.coroutineContext.cancelChildren()
//        reset()
//    }
//
//    private fun processStreamData(chunk: FloatArray) {
//        var offset = 0
//        while (offset < chunk.size) {
//            val toCopy = minOf(windowSize - bufferIndex, chunk.size - offset)
//            System.arraycopy(chunk, offset, buffer, bufferIndex, toCopy)
//            bufferIndex += toCopy
//            offset += toCopy
//
//            if (bufferIndex == windowSize) {
//                runVad(buffer)
//                bufferIndex = 0
//            }
//        }
//    }
//
//    private fun ensureState(): Array<Array<FloatArray>> {
//        val current = hiddenState
//        if (current != null) return current
//        // Initialize zeros [2,1,128]
//        val state = Array(2) { Array(1) { FloatArray(128) } }
//        hiddenState = state
//        return state
//    }
//
//    private fun runVad(chunk: FloatArray) {
//        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(chunk), longArrayOf(1, windowSize.toLong()))
//        val stateInput = OnnxTensor.createTensor(env, ensureState())
//
//        val inputs = mutableMapOf<String, OnnxTensor>()
//        inputs["input"] = inputTensor
//        inputs["state"] = stateInput
//        inputs["sr"] = srTensor
//
//        session.run(inputs).use { results ->
//            val speechProb = (results.get("output").get().value as Array<FloatArray>)[0][0]
//            @Suppress("UNCHECKED_CAST")
//            val nextState = results.get("stateN").get().value as Array<Array<FloatArray>>
//            hiddenState = nextState
//
//            if (speechProb > 0.5 && !speechActive) {
//                speechActive = true
//                onSpeechStart()
//            } else if (speechProb <= 0.5 && speechActive) {
//                speechActive = false
//                onSpeechEnd()
//            }
//        }
//
//        stateInput.close()
//        inputTensor.close()
//    }
//
//    private fun reset() {
//        hiddenState = null
//        bufferIndex = 0
//        speechActive = false
//    }
//
//    private fun onSpeechStart() {
//        log.info("Speech started")
//    }
//
//    private fun onSpeechEnd() {
//        log.info("Speech ended")
//    }
//
//    companion object {
//        fun getInstance(): VadService = service()
//    }
//}
//
//

package com.github.flydzen.idevoiceassistant.vad

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.slf4j.LoggerFactory
import java.nio.FloatBuffer

class SileroChunkSpeechEstimator(
    private val sampleRate: Long = 16_000L,
    private val windowSize: Int = 512,
    private val threshold: Float = 0.5f
) : ChunkSpeechEstimator {

    private val log = LoggerFactory.getLogger(SileroChunkSpeechEstimator::class.java)

    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val srTensor: OnnxTensor

    // Silero VAD: состояние float32[2, 1, 128], сохраняется между вызовами
    private var hiddenState: Array<Array<FloatArray>>? = null

    init {
        val modelBytes = javaClass.getResourceAsStream("/models/silero_vad.onnx")
            ?.readBytes()
            ?: error("VAD model not found in resources!")
        session = env.createSession(modelBytes)
        srTensor = OnnxTensor.createTensor(env, sampleRate) // int64 scalar
    }

    override fun isSpeech(chunk: FloatArray): Boolean {
        if (chunk.isEmpty()) return false
        if (chunk.size != windowSize) {
            log.warn("Silero VAD received chunk of size ${chunk.size}, expected $windowSize — skipping")
            return false
        }
        val prob = infer(chunk)
        return prob > threshold
    }

    fun reset() {
        hiddenState = null
    }

    private fun ensureState(): Array<Array<FloatArray>> {
        val current = hiddenState
        if (current != null) return current
        // Инициализация нулями [2,1,128]
        val state = Array(2) { Array(1) { FloatArray(128) } }
        hiddenState = state
        return state
    }

    // Единственный вызов инференса по готовому окну
    private fun infer(window: FloatArray): Float {
        val inputTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(window),
            longArrayOf(1, windowSize.toLong())
        )
        val stateInput = OnnxTensor.createTensor(env, ensureState())

        return try {
            val inputs = hashMapOf<String, OnnxTensor>(
                "input" to inputTensor,
                "state" to stateInput,
                "sr" to srTensor
            )
            session.run(inputs).use { results ->
                val speechProb = (results.get("output").get().value as Array<FloatArray>)[0][0]
                @Suppress("UNCHECKED_CAST")
                val nextState = results.get("stateN").get().value as Array<Array<FloatArray>>
                hiddenState = nextState
                speechProb
            }
        } catch (t: Throwable) {
            log.warn("Silero VAD inference error", t)
            0f
        } finally {
            try {
                stateInput.close()
            } catch (_: Throwable) {}
            try {
                inputTensor.close()
            } catch (_: Throwable) {}
        }
    }
}