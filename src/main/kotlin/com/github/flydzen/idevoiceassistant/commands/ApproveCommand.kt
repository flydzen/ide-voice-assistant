package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

class ApproveCommand(val project: Project) : Command() {
    private var rollbackData: EditorSnapshot? = null

    override fun process() {
        invokeLater {
            val editor = project.service<FileEditorManager>().selectedTextEditor ?: return@invokeLater
            rollbackData = EditorSnapshot.create(project, editor)
            AICodeGenActionsExecutor.acceptAllChanges(editor)
        }
        // TODO: approve other commands
    }

    override fun rollback() {
        rollbackData?.rollbackEditor(project)
    }
}