package com.github.flydzen.idevoiceassistant.commands

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class EditorSnapshot private constructor(
    val selectedFile: VirtualFile,
    val documentText: String,
    val caretOffset: Int,
    val selectionStart: Int,
    val selectionEnd: Int
) {
    fun rollbackEditor(project: Project) {
        invokeLater {
            val editor = project.service<FileEditorManager>().openTextEditor(
                OpenFileDescriptor(project, selectedFile),
                true
            ) ?: return@invokeLater

            WriteCommandAction.runWriteCommandAction(project) {
                if (editor.document.text != documentText) {
                    editor.document.setText(documentText)
                }

                editor.caretModel.moveToOffset(caretOffset)
                if (selectionStart != selectionEnd) {
                    editor.selectionModel.setSelection(selectionStart, selectionEnd)
                }
            }
        }
    }

    companion object {
        fun create(project: Project, editor: Editor): EditorSnapshot? {
            val selectedFile = project.service<FileEditorManager>().selectedFiles.firstOrNull() ?: return null
            return EditorSnapshot(
                selectedFile = selectedFile,
                documentText = editor.document.text,
                caretOffset = editor.caretModel.offset,
                selectionStart = editor.selectionModel.selectionStart,
                selectionEnd = editor.selectionModel.selectionEnd
            )
        }
    }
}