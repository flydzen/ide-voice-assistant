package com.github.flydzen.idevoiceassistant.audio

interface AudioListener {
    fun onStart() {}
    fun onStop() {}
    fun onError(t: Throwable) {}
}