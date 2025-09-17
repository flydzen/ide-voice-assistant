package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.Config
import com.github.flydzen.idevoiceassistant.Util
import com.github.flydzen.idevoiceassistant.audio.saveWave
import com.github.flydzen.idevoiceassistant.vad.AmplitudeChunkSpeechEstimator
import com.github.flydzen.idevoiceassistant.vad.ChunkSpeechEstimator
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@Service(Service.Level.PROJECT)
class VADService(
    private val project: Project,
    scope: CoroutineScope,
): Disposable {
    val estimator: ChunkSpeechEstimator = AmplitudeChunkSpeechEstimator()

    val outputChannel = Channel<Path>(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val LOG: Logger = thisLogger()

    // Окно для VAD (Silero ожидает 512 семплов при 16кГц)
    private val windowSamples = 512
    private val windowMs = windowSamples * 1000 / Config.audioFormat.sampleRate
    private val windowBytes = windowSamples * Config.bytesPerSample

    // Сколько «окон тишины» ждём, чтобы закрыть фразу (~192 мс при 512 сэмплах)
    private val endSilenceWindows = 16
    private val minPhraseMs = 500

    // Буферизация входных СЕМПЛОВ и исходных байтов на размер окна
    private val windowFloat = FloatArray(windowSamples)
    private var windowFloatFill = 0
    private val windowRaw = ByteArray(windowBytes)
    private var windowRawFill = 0

    // Для сборки PCM16LE семплов из байтов
    private var pendingLo: Byte? = null

    // Состояние фразы
    private var inSpeech = false
    private var silenceCounter = 0
    private var phraseCounter = 0

    private var phraseBuffer: ByteArrayOutputStream? = null

    private val _volumeLevel = MutableStateFlow(0.0f)
    val volumeLevel: StateFlow<Float> = _volumeLevel.asStateFlow()

    init {
        scope.launch {
            project.service<RecordAudioService>()
                .inputChannel
                .receiveAsFlow()
                .collect { b ->
                    b.forEach {
                        onByte(it)
                    }
                }
        }
    }

    private suspend fun onByte(b: Byte) {
        val lo = pendingLo
        if (lo == null) {
            pendingLo = b
            return
        }
        // Собрали один 16-битный семпл (LE)
        val hi = b
        val loInt = lo.toInt() and 0xFF
        val hiInt = hi.toInt()
        val s = ((hiInt shl 8) or loInt).toShort().toInt()
        val sampleFloat = if (s >= 0) s / 32767f else s / 32768f

        // Пишем исходные байты в окно
        if (windowRawFill + 2 <= windowRaw.size) {
            windowRaw[windowRawFill++] = lo
            windowRaw[windowRawFill++] = hi
        }

        // Пишем нормализованный семпл в окно
        if (windowFloatFill < windowFloat.size) {
            windowFloat[windowFloatFill++] = sampleFloat
        }

        pendingLo = null

        // Когда окно заполнено — проверяем
        if (windowFloatFill >= windowSamples) {
            processWindow(windowFloat, windowRaw, windowRawFill)
            windowFloatFill = 0
            windowRawFill = 0
        }
    }

    private suspend fun processWindow(floatWindow: FloatArray, rawBytes: ByteArray, rawLen: Int) {

        val probability = estimator.getProbability(floatWindow)
        val speech = probability > 0.5f
        _volumeLevel.emit(probability)

        if (!inSpeech) {
            if (speech) {
                inSpeech = true
                silenceCounter = 0
                phraseCounter = 1
                phraseBuffer = ByteArrayOutputStream().also { it.write(rawBytes, 0, rawLen) }
                Util.LOG.info("VAD: начало фразы")
            } else {
                // тишина, остаёмся вне речи
            }
        } else {
            // речь активна — добавляем исходные байты окна и проверяем окончание
            phraseBuffer?.write(rawBytes, 0, rawLen)
            if (speech) {
                silenceCounter = 0
                phraseCounter++
            } else {
                silenceCounter++
                if (silenceCounter >= endSilenceWindows) {
                    finishPhrase()
                    inSpeech = false
                    silenceCounter = 0
                }
            }
        }
    }

    private suspend fun finishPhrase() {
        val buffer = phraseBuffer ?: return
        phraseBuffer = null
        try {
            val durationMs = phraseCounter * windowMs
            if (durationMs < minPhraseMs) {
                Util.LOG.info("VAD: short phrase, $durationMs ms")
                return
            }

            val pcm = buffer.toByteArray()
            val path = createOutputPath()
            saveWave(pcm, path.toFile())
            outputChannel.send(path)
            Util.LOG.info("VAD: конец фразы - $path")
        } catch (e: Throwable) {
            LOG.warn("Не удалось сохранить фразу в WAV", e)
        }
    }

    private fun createOutputPath(): Path {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
        return try {
            Files.createTempFile("utterance_$ts", ".wav")
        } catch (_: IOException) {
            Path.of("utterance_$ts.wav")
        }
    }

    override fun dispose() {}
}