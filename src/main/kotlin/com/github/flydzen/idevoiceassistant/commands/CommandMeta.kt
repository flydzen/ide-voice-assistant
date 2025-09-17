package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.Parameter

interface CommandMeta {
    val toolName: String
    val description: String
    val parameters: List<Parameter>
}
