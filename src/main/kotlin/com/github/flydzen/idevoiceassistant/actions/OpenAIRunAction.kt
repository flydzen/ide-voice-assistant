package com.github.flydzen.idevoiceassistant.actions

import com.github.flydzen.idevoiceassistant.openai.OpenAIResponsesClient
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.Messages

class OpenAIRunAction : AnAction("Run OpenAI Voice Assistant") {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project

        if (project == null) {
            Messages.showErrorDialog("No project is open", "Error")
            return
        }

        val textToProcess = getTextToProcess(editor)

        if (textToProcess == null || textToProcess.isEmpty()) {
            Messages.showInfoMessage("No text selected or cursor is empty", "Info")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = OpenAIResponsesClient.run(textToProcess)
                println(result)
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        "Error running OpenAI request: ${e.message}",
                        "OpenAI Error"
                    )
                }
            }
        }
    }

    private fun getTextToProcess(editor: Editor?): String? {
        if (editor == null) return ""

        val selectionModel = editor.selectionModel

        if (selectionModel.hasSelection()) {
            return selectionModel.selectedText ?: ""
        }
        return null
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}