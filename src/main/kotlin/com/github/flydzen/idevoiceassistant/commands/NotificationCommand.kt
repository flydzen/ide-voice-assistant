package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils
import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.project.Project

class NotificationCommand(
    private val project: Project,
    private val text: String
) : Command() {
    override val toolName = "idontknow"
    override val description: String = "If you don't know what to do, intent is unclear, or user must provide more information, " +
            "call idontknow(reason, research=False). " +
            "If the query needs deeper reasoning/research, " +
            "call idontknow(reason, research=True) to escalate to a heavier model."
    override val parameters: List<Parameter> = listOf(
        Parameter("reason", "string", "The reason you don't know"),
        Parameter("research", "boolean", "Whether to redirect question to more powerfull model. Don't use if you need some information from user"),
    )

    override fun process() {
        Utils.showNotification(project, "Not recognized: $text")
    }

    override fun rollback() {}

    companion object {
        fun build(project: Project, params: Map<String, Any>): NotificationCommand {
            val text = params["text"] as String
            return NotificationCommand(project, text)
        }
    }
}