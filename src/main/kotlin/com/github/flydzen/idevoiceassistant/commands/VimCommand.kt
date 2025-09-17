package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.services.VimScriptExecutionService
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager

class VimCommand(val command: String, val project: Project) : Command() {
    private var rollbackData: EditorSnapshot? = null

    override fun process() {
        invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val editorComponent = fileEditorManager.selectedTextEditor?.contentComponent
            if (editorComponent != null) {
                IdeFocusManager.getInstance(project).requestFocus(editorComponent, true)
            }
            val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater
            rollbackData = EditorSnapshot.create(project, editor)
            val modifiedScript = modifyVimCommandToVimScript(command)
            println("Vim original command: $command")
            println("Vim modified command: $modifiedScript")
            VimScriptExecutionService.getInstance(project).execute(modifiedScript)
        }
    }

    private fun modifyVimCommandToVimScript(command: String): String {
        return if (!command.startsWith(":")) {
            ":normal $command<cr>"
        } else command
    }

    override fun rollback() {
        rollbackData?.rollbackEditor(project)
    }

    override fun toString(): String = "VimCommand(command='$command')"
}