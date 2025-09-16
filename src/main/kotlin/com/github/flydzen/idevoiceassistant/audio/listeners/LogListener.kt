package com.github.flydzen.idevoiceassistant.audio.listeners

import com.intellij.openapi.diagnostic.thisLogger

class LogListener : AudioListener {
    override fun onStart() {
        LOG.info("Audio capture started")
    }

    override fun onStop() {
        LOG.info("Audio capture stopped")
    }

    override fun onError(t: Throwable) {
        LOG.error("Audio capture error", t)
    }

    companion object {
        private val LOG = thisLogger()
    }
}