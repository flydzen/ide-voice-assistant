package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.codeGeneration.AICodeGenActionsExecutor
import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

class ApproveCommand(private val project: Project) : Command() {
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

    companion object : CommandMeta {
        override val toolName: String = "approve"
        override val description: String = "Approve, changes. Any synonyms of approve, approve, accept should trigger that command"
        override val parameters: List<Parameter> = emptyList()

        override fun build(project: Project, previousCommand: Command?, params: Map<String, Any>): Command = build(project)

        fun build(project: Project): ApproveCommand = ApproveCommand(project)
    }
}