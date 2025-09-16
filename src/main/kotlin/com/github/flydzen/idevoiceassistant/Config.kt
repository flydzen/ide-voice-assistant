package com.github.flydzen.idevoiceassistant

import javax.sound.sampled.AudioFormat

object Config {
    val audioFormat = AudioFormat(
        /* sampleRate = */ 16_000f,
        /* sampleSizeInBits = */ 16,
        /* channels = */ 1,
        /* signed = */ true,
        /* bigEndian = */ false
    )

    val bytesPerSample = audioFormat.sampleSizeInBits / 8
}