package com.github.flydzen.idevoiceassistant.audio

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*
import kotlin.io.path.createTempFile

class AudioCaptureTask(seconds: Int) {
    private val duration = TimeUnit.SECONDS.toMillis(seconds.toLong())

    @Volatile
    private var listener: AudioListener? = null

    init {
        require(seconds > 0) { "Seconds must be positive" }
        require(seconds <= MAX_TIME_SECONDS) { "Seconds must be less than $MAX_TIME_SECONDS" }
    }

    suspend fun run() = coroutineScope {
        val microphone = createMicrophone()
        val tempFile = createTempFile()

        val stopper = launch {
            delay(duration)
            microphone.stopCapture()
        }

        try {
            microphone.startCapture(tempFile)
        } catch(e: Exception) {
            LOG.error("Audio capture error", e)
            listener?.onError(e)
        }
        finally {
            stopper.cancel()
            microphone.stopCapture()
        }
    }

    private fun createMicrophone(): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, FORMAT)
        val microphone = (AudioSystem.getLine(info) as TargetDataLine)
        microphone.open(FORMAT, BUFFER_SIZE_BYTES)
        return microphone
    }

    private fun createTempFile(): File {
        val file = createTempFile(prefix = "recording-", suffix = ".wav").toFile()
        LOG.info("Audio file created: ${file.absolutePath}")
        return file
    }

    private suspend fun TargetDataLine.startCapture(file: File) {
        start()
        val out = AudioInputStream(this)
        listener?.onStart()
        LOG.info("Audio capture started")
        withContext(Dispatchers.IO) {
            AudioSystem.write(out, AudioFileFormat.Type.WAVE, file)
        }
    }

    private fun TargetDataLine.stopCapture() {
        if (isActive) {
            stop()
        }
        if (isOpen) {
            close()
        }
        listener?.onStop()
        LOG.info("Audio capture stopped")
    }

    companion object {
        private const val BUFFER_SIZE_BYTES: Int = 4096
        private const val MAX_TIME_SECONDS: Int = 60

        private val FORMAT: AudioFormat = AudioFormat(
            /* sampleRate = */ 48_000f,
            /* sampleSizeInBits = */ 16,
            /* channels = */ 1,
            /* signed = */ true,
            /* bigEndian = */ true
        )

        private val LOG: Logger = thisLogger()
    }
}