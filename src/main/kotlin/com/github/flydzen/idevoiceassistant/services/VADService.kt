package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.vad.AmplitudeChunkSpeechEstimator
import com.github.flydzen.idevoiceassistant.vad.ChunkSpeechEstimator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service(Service.Level.PROJECT)
class VADService(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    val estimator: ChunkSpeechEstimator = AmplitudeChunkSpeechEstimator()

    private val _outputFlow = MutableStateFlow<Path?>(null)
    val outputFlow = _outputFlow.asSharedFlow()

    private val LOG: Logger = thisLogger()

    // Настройки аудио
    private val sampleRate = 16_000
    private val channels = 1
    private val bitsPerSample = 16
    private val bytesPerSample = bitsPerSample / 8

    // Окно для VAD (Silero ожидает 512 семплов при 16кГц)
    private val windowSamples = 512
    private val windowBytes = windowSamples * bytesPerSample

    // Сколько «окон тишины» ждём, чтобы закрыть фразу (~192 мс при 512 сэмплах)
    private val endSilenceWindows = 6

    private var job: Job? = null

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
    private var phraseBuffer: ByteArrayOutputStream? = null

    init {
        start()
    }

    private fun start() {
        job?.cancel()
        job = scope.launch {
            project.service<RecordAudioService>()
                .inputFlow
                .filterNotNull()
                .collect { b ->
                    onByte(b)
                }
        }
    }

    private fun onByte(b: Byte) {
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

    private fun processWindow(floatWindow: FloatArray, rawBytes: ByteArray, rawLen: Int) {
        val speech = estimator.isSpeech(floatWindow)

        if (!inSpeech) {
            if (speech) {
                inSpeech = true
                silenceCounter = 0
                phraseBuffer = ByteArrayOutputStream().also { it.write(rawBytes, 0, rawLen) }
                LOG.info("начало фразы")
            } else {
                // тишина, остаёмся вне речи
            }
        } else {
            // речь активна — добавляем исходные байты окна и проверяем окончание
            phraseBuffer?.write(rawBytes, 0, rawLen)
            if (speech) {
                silenceCounter = 0
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

    private fun finishPhrase() {
        val buffer = phraseBuffer ?: return
        phraseBuffer = null
        try {
            val pcm = buffer.toByteArray()
            val path = createOutputPath()
            writeWav(path, pcm, sampleRate, bitsPerSample, channels)
            _outputFlow.value = path
            LOG.info("конец фразы")
            LOG.info("Фраза сохранена: $path")
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

    // Конвертация блока PCM16LE -> float32 [-1, 1]
    private fun i16LeBlockToFloat(src: ByteArray, length: Int): FloatArray {
        val out = FloatArray(length / 2)
        var i = 0
        var j = 0
        while (i + 1 < length) {
            val lo = src[i].toInt() and 0xFF
            val hi = src[i + 1].toInt()
            val s = ((hi shl 8) or lo).toShort().toInt()
            out[j++] = if (s >= 0) s / 32767f else s / 32768f
            i += 2
        }
        return out
    }

    private fun writeWav(
        path: Path,
        pcmLe: ByteArray,
        sampleRate: Int,
        bitsPerSample: Int,
        channels: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmLe.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(chunkSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                  // PCM subchunk size
        header.putShort(1)                 // Audio format 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        Files.newOutputStream(path).use { out ->
            out.write(header.array())
            out.write(pcmLe)
        }
    }

    fun dispose() {
        try {
            job?.cancel()
        } catch (_: Throwable) {
        }
    }
}