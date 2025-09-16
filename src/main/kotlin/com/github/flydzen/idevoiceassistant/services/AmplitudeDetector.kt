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
import kotlinx.coroutines.flow.collect
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
class AmplitudeDetector(
    private val project: Project,
    private val scope: CoroutineScope,
) {
    private val LOG: Logger = thisLogger()

    // Настройки аудио
    private val sampleRate = 16_000
    private val channels = 1
    private val bitsPerSample = 16
    private val bytesPerSample = bitsPerSample / 8

    // Фрейм ~10мс при 16кГц
    private val frameSamples = 160
    private val frameBytes = frameSamples * bytesPerSample

    // Гистерезис VAD на уровне детектора
    private val endSilenceFrames = 20 // ~200мс тишины для завершения фразы

    private var job: Job? = null

    // Буферизация входных байт в кадры
    private val frameBuf = ByteArray(frameBytes)
    private var frameFill = 0
    private var pendingLo: Byte? = null // для сборки int16 из потока байт

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
        // собрали 2 байта -> кладём в frameBuf
        frameBuf[frameFill++] = lo
        frameBuf[frameFill++] = b
        pendingLo = null

        if (frameFill >= frameBytes) {
            // есть полный кадр, обрабатываем
            val floats = i16LeBlockToFloat(frameBuf, frameFill)
            processFrame(floats, frameBuf, 0, frameFill)
            frameFill = 0
        }
    }

    private fun processFrame(floatFrame: FloatArray, rawBytes: ByteArray, off: Int, len: Int) {
        val speech = estimator.isSpeech(floatFrame)

        if (!inSpeech) {
            if (speech) {
                inSpeech = true
                silenceCounter = 0
                phraseBuffer = ByteArrayOutputStream().also { it.write(rawBytes, off, len) }
                LOG.info("начало фразы")
            } else {
                // тишина, остаёмся вне речи
            }
        } else {
            // речь активна — добавляем в фразу и проверяем окончание
            phraseBuffer?.write(rawBytes, off, len)
            if (speech) {
                silenceCounter = 0
            } else {
                silenceCounter++
                if (silenceCounter >= endSilenceFrames) {
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