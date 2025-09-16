package com.github.flydzen.idevoiceassistant.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.collect
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlin.math.sqrt

@Service(Service.Level.PROJECT)
class AmplitudeVADetector(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    private val _outputFlow = MutableStateFlow<Path?>(null)
    val outputFlow = _outputFlow.asSharedFlow()

    private val sampleRate: Int = 16_000         // Гц
    private val channels: Int = 1
    private val bitsPerSample: Int = 16
    private val bytesPerSample: Int = bitsPerSample / 8

    // блоки по ~10ms: при 16кГц и 1 канал = 160 сэмплов, 320 байт
    private val frameSamples: Int = 160
    private val frameBytes: Int = frameSamples * bytesPerSample

    // Порог амплитуды для начала/поддержания речи (RMS в диапазоне 0..1 для int16)
    private val startThresholdRms: Double = 0.04      // начало фразы
    private val continueThresholdRms: Double = 0.025  // поддержание речи (гистерезис)
    private val endSilenceFrames: Int = 30            // 30 * 10мс = 300мс тишины для окончания

    private val LOG: Logger = thisLogger()

    private var job: Job? = null

    // Временные буферы
    private val frameBuffer = ByteArray(frameBytes)
    private var frameFill = 0

    private var inSpeech: Boolean = false
    private var silenceCounter: Int = 0

    private var phraseBuffer: ByteArrayOutputStream? = null

    init {
        start()
    }

    private fun start() {
        job?.cancel()
        job = scope.launch {
            val recordService = project.service<RecordAudioService>()
            recordService.inputFlow
                .filterNotNull()
                .collect { b ->
                    onByteReceived(b)
                }
        }
    }

    private fun onByteReceived(b: Byte) {
        frameBuffer[frameFill++] = b

        // при заполнении фрейма считаем показатели
        if (frameFill >= frameBytes) {
            val rms = computeRmsInt16Le(frameBuffer, 0, frameBytes)
            handleVadWithRms(rms, frameBuffer, 0, frameBytes)
            frameFill = 0
        }
    }

    private fun handleVadWithRms(rms: Double, data: ByteArray, off: Int, len: Int) {
        if (!inSpeech) {
            // Пока тишина — ждём превышение порога начала
            if (rms >= startThresholdRms) {
                inSpeech = true
                silenceCounter = 0
                phraseBuffer = ByteArrayOutputStream().also {
                    it.write(data, off, len)
                }
                LOG.info("начало фразы")
            } else {
                // остаёмся в тишине
            }
        } else {
            // Речь активна
            val contThreshold = continueThresholdRms
            phraseBuffer?.write(data, off, len)
            if (rms < contThreshold) {
                silenceCounter++
                if (silenceCounter >= endSilenceFrames) {
                    // Конец фразы
                    finishPhrase()
                    inSpeech = false
                    silenceCounter = 0
                }
            } else {
                // Речь продолжается — сбрасываем счётчик тишины
                silenceCounter = 0
            }
        }
    }

    private fun finishPhrase() {
        val buffer = phraseBuffer
        phraseBuffer = null
        if (buffer == null) return

        try {
            val rawPcm = buffer.toByteArray()
            val outputPath = createOutputPath()
            writeWavFile(
                path = outputPath,
                pcmLe = rawPcm,
                sampleRate = sampleRate,
                bitsPerSample = bitsPerSample,
                channels = channels
            )
            _outputFlow.value = outputPath
            LOG.info("конец фразы")
            LOG.info("Фраза сохранена: $outputPath")
        } catch (e: Throwable) {
            LOG.warn("Не удалось сохранить фразу в WAV", e)
        }
    }

    private fun createOutputPath(): Path {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"))
        return try {
            // Сохраняем во временную директорию
            Files.createTempFile("utterance_$timestamp", ".wav")
        } catch (e: IOException) {
            // Фолбэк — если не получилось во временную, пробуем в рабочую директорию
            Path.of("utterance_$timestamp.wav")
        }
    }

    // RMS для int16 little-endian; возвращаем значение в диапазоне [0..1]
    private fun computeRmsInt16Le(bytes: ByteArray, off: Int, len: Int): Double {
        if (len <= 0) return 0.0
        val sampleCount = len / 2
        if (sampleCount == 0) return 0.0

        var sumSq = 0.0
        var i = off
        val end = off + len
        while (i + 1 < end) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() // signed; will shift with sign — ок
            val sample = (hi shl 8) or lo
            val s = if (sample > Short.MAX_VALUE) sample - 0x1_0000 else sample // нормализуем в signed 16
            val norm = s / 32768.0
            sumSq += norm * norm
            i += 2
        }
        val meanSq = sumSq / sampleCount
        return sqrt(meanSq)
    }

    private fun writeWavFile(
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
        } catch (_: Throwable) { /* ignore */ }
    }
}