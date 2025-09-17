package com.github.flydzen.idevoiceassistant.commands

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

sealed class Command {
    abstract fun process()
    abstract fun rollback()

    class RollbackEditorData(
        val editorState: EditorState?
    )

    data class EditorState(
        val virtualFile: VirtualFile,
        val documentText: String,
        val caretOffset: Int,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    companion object {
        fun RollbackEditorData.rollbackEditor(project: Project) {
            val data = editorState ?: return

            invokeLater {
                val editor = project.service<FileEditorManager>().openTextEditor(
                    OpenFileDescriptor(project, data.virtualFile),
                    true
                ) ?: return@invokeLater

                WriteCommandAction.runWriteCommandAction(project) {
                    if (editor.document.text != data.documentText) {
                        editor.document.setText(data.documentText)
                    }

                    editor.caretModel.moveToOffset(data.caretOffset)
                    if (data.selectionStart != data.selectionEnd) {
                        editor.selectionModel.setSelection(data.selectionStart, data.selectionEnd)
                    }
                }
            }
        }

        fun collectEditorRollbackData(project: Project, editor: Editor): RollbackEditorData {
            return RollbackEditorData(
                editorState = project.service<FileEditorManager>().selectedFiles.firstOrNull()?.let { file ->
                    EditorState(
                        virtualFile = file,
                        documentText = editor.document.text,
                        caretOffset = editor.caretModel.offset,
                        selectionStart = editor.selectionModel.selectionStart,
                        selectionEnd = editor.selectionModel.selectionEnd
                    )
                }
            )
        }
    }
}
