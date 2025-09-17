package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EnterTextCommand private constructor(
    private val project: Project,
    private val text: String
) : Command() {
    private var rollbackData: RollbackData? = null

    data class RollbackData(
        val insertOffset: Int,
        val insertLength: Int,
        val virtualFile: VirtualFile?
    )

    override fun process() {
        invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater
            val offset = editor.caretModel.offset

            rollbackData = RollbackData(
                insertOffset = offset,
                insertLength = text.length,
                virtualFile = fileEditorManager.selectedFiles.firstOrNull()
            )

            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                val caretModel = editor.caretModel
                val offset = caretModel.offset

                document.insertString(offset, text)
                caretModel.moveToOffset(offset + text.length)
            }

        }
    }

    override fun rollback() {
        invokeLater {
            val data = rollbackData ?: return@invokeLater
            val fileEditorManager = FileEditorManager.getInstance(project)

            val currentFile = fileEditorManager.selectedFiles.firstOrNull()
            if (currentFile != data.virtualFile) {
                data.virtualFile?.let { file ->
                    fileEditorManager.openFile(file, true)
                }
            }

            val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater

            WriteCommandAction.runWriteCommandAction(project) {
                val document = editor.document
                val endOffset = data.insertOffset + data.insertLength

                if (endOffset <= document.textLength) {
                    val actualText =
                        document.getText(com.intellij.openapi.util.TextRange(data.insertOffset, endOffset))
                    if (actualText == text) {
                        document.deleteString(data.insertOffset, endOffset)
                        editor.caretModel.moveToOffset(data.insertOffset)
                    }
                }
            }
        }
    }

    override fun toString(): String = "EnterText(text='$text')"

    companion object : CommandMeta {
        override val toolName: String = "insert"
        override val description: String = "Insert text at the cursor"
        override val parameters: List<Parameter> = listOf(
            Parameter("text", "string", "Text to insert")
        )

        override fun build(project: Project, previousCommand: Command?, params: Map<String, Any>): Command = build(project, params)

        fun build(project: Project, params: Map<String, Any>): EnterTextCommand {
            val text = params["text"] as String
            return build(project, text)
        }

        fun build(project: Project, text: String): EnterTextCommand = EnterTextCommand(project, text)
    }
}