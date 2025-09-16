package com.github.flydzen.idevoiceassistant.audio

import com.github.flydzen.idevoiceassistant.audio.listeners.AudioListener
import com.github.flydzen.idevoiceassistant.audio.listeners.LogListener
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
    private var listeners: MutableList<AudioListener> = mutableListOf()

    init {
        require(seconds > 0) { "Seconds must be positive" }
        require(seconds <= MAX_TIME_SECONDS) { "Seconds must be less than $MAX_TIME_SECONDS" }

        listeners.add(LogListener())
    }

    suspend fun run() = coroutineScope {
        val microphone = createMicrophone()
        val tempFile = createTempFile()

        val stopper = launch {
            delay(duration)
            microphone.stopCapture()
            triggerOnStop()
        }

        try {
            microphone.startCapture(tempFile)
            triggerOnStart()
        } catch(e: Exception) {
            triggerOnError(e)
        }
        finally {
            stopper.cancel()
            microphone.stopCapture()
            triggerOnStop()
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
    }

    private fun triggerOnStart() {
        listeners.forEach { it.onStart() }
    }

    private fun triggerOnStop() {
        listeners.forEach { it.onStop() }
    }

    private fun triggerOnError(t: Throwable) {
        listeners.forEach { it.onError(t) }
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