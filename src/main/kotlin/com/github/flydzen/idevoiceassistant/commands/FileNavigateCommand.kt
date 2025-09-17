package com.github.flydzen.idevoiceassistant.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class FileNavigateCommand(
    val fileName: String,
    val project: Project,
) : Command() {
    private var rollbackData: RollbackData? = null

    data class RollbackData(
        val previousFile: VirtualFile?,
        val previousCaretOffset: Int
    )

    override fun process() {
        invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)
            val currentEditor = fileEditorManager.selectedTextEditor

            rollbackData = RollbackData(
                previousFile = fileEditorManager.selectedFiles.firstOrNull(),
                previousCaretOffset = currentEditor?.caretModel?.offset ?: 0
            )

            ApplicationManager.getApplication().executeOnPooledThread {
                runReadAction {
                    val allFileNames = FilenameIndex.getAllFilenames(project)
                    val properFileName = allFileNames.find { it.equals(fileName, ignoreCase = true) } ?: return@runReadAction
                    val file = FilenameIndex
                        .getVirtualFilesByName(
                            /* name = */ properFileName,
                            /* scope = */ GlobalSearchScope.projectScope(project)
                        )
                        .firstOrNull() ?: return@runReadAction

                    invokeLater {
                        openFileInEditor(file, project)
                    }
                }
            }
        }
    }

    override fun rollback() {
        val data = rollbackData ?: return

        invokeLater {
            val fileEditorManager = FileEditorManager.getInstance(project)

            data.previousFile?.let { file ->
                val editor = fileEditorManager.openTextEditor(
                    OpenFileDescriptor(project, file),
                    true
                )

                editor?.caretModel?.moveToOffset(data.previousCaretOffset)
            }
        }
    }

    private fun openFileInEditor(file: VirtualFile, project: Project, line: Int? = null) {
        val fileEditorManager = FileEditorManager.getInstance(project)

        val editor = fileEditorManager.openTextEditor(
            OpenFileDescriptor(project, file),
            true
        )

        if (line != null && editor != null) {
            val document = editor.document
            val lineCount = document.lineCount

            val targetLine = (line - 1).coerceIn(0, lineCount - 1)
            val offset = document.getLineStartOffset(targetLine)

            editor.caretModel.moveToOffset(offset)
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
        }
    }

    override fun toString(): String = "FileNavigate(fileName='$fileName')"
}