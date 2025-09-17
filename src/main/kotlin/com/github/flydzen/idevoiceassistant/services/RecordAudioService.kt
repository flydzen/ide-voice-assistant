package com.github.flydzen.idevoiceassistant.services

import com.github.flydzen.idevoiceassistant.Config
import com.github.flydzen.idevoiceassistant.Utils
import com.github.flydzen.idevoiceassistant.audio.listeners.AudioListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

@Service(Service.Level.PROJECT)
class RecordAudioService(
    @Suppress("unused") private val project: Project,
    private val scope: CoroutineScope
) : Disposable {
    private val isActive = AtomicBoolean(false)

    @Volatile
    private var listeners: MutableList<AudioListener> = mutableListOf()

    @get:Synchronized
    private val microphone = createMicrophone()

    val inputChannel = Channel<ByteArray>(capacity = Channel.UNLIMITED)

    fun start() {
        if (!lock()) {
            LOG.warn("RecordAudioService is already active")
            return
        }

        try {
            triggerOnStart()
            startCapture()
        } catch (e: Exception) {
            triggerOnError(e)
        }
    }

    fun stop() {
        stopCapture()
        triggerOnStop()
        unlock()
    }

    fun toggle() {
        if (isActive.get()) {
            stop()
        } else {
            start()
        }
    }

    private fun lock(): Boolean {
        return isActive.compareAndSet(false, true)
    }

    private fun unlock() {
        isActive.set(false)
    }

    private fun createMicrophone(): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, Config.audioFormat)
        val microphone = (AudioSystem.getLine(info) as TargetDataLine)
        microphone.open(Config.audioFormat, Config.BUFFER_SIZE_BYTES)
        return microphone
    }

    private fun startCapture() {
        microphone.start()
        scope.launch {
            withContext(Dispatchers.IO) {
                emitPcmBytes()
            }
        }
    }

    private suspend fun emitPcmBytes() {
        val buffer = ByteArray(Config.BUFFER_SIZE_BYTES)
        val output = mutableListOf<Byte>()
        try {
            while (this@RecordAudioService.isActive.get()) {
                val n = microphone.read(buffer, 0, buffer.size)
                if (n <= 0) {
                    LOG.debug("No data from microphone (n=$n)")
                    continue
                }
                val data = buffer.copyOf(n)
                inputChannel.send(data)
                output.addAll(data.toList())
            }
        } catch (t: Throwable) {
            LOG.warn("Capture read interrupted: ${t.message}")
            triggerOnError(t)
        } finally {
            if (output.isNotEmpty()) {
                val file = Utils.saveWave(output.toByteArray(), "audio-recording-")
                LOG.info("Audio capture finished: $file")
            }
        }
    }

    private fun stopCapture() {
        if (microphone.isActive) {
            microphone.stop()
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

    override fun dispose() {
        if (microphone.isOpen) {
            microphone.close()
        }
    }


    companion object {
        private val LOG: Logger = thisLogger()
    }
}