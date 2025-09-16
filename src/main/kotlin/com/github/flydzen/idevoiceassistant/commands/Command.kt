package com.github.flydzen.idevoiceassistant.commands

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.vfs.VirtualFile

sealed class Command {
    abstract fun process()
    class EnterText(val text: String, val project: Project) : Command() {
        override fun process() {
            invokeLater {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor ?: return@invokeLater

                WriteCommandAction.runWriteCommandAction(project) {
                    val document = editor.document
                    val caretModel = editor.caretModel
                    val offset = caretModel.offset

                    document.insertString(offset, text)
                    caretModel.moveToOffset(offset + text.length)
                }

            }
        }
    }

    class EditorNavigate(
        val fileName: String,
        val project: Project,
        val packagePrefix: String? = null,
        val line: Int? = null,
    ) : Command() {
        override fun process() {
            invokeLater {
                // Find files by name
                val files = FilenameIndex.getFilesByName(
                    project,
                    fileName,
                    GlobalSearchScope.projectScope(project)
                )

                val targetFile = if (packagePrefix != null) {
                    // Filter by package prefix if provided
                    files.find { file ->
                        val filePath = file.virtualFile.path
                        val packagePath = packagePrefix.replace('.', '/')
                        filePath.contains(packagePath)
                    }
                } else {
                    // Take first match if no package prefix
                    files.firstOrNull()
                }

                targetFile?.let { file ->
                    openFileInEditor(file.virtualFile, project, line)
                }
            }
        }

        private fun openFileInEditor(file: VirtualFile, project: Project, line: Int? = null) {
            val fileEditorManager = FileEditorManager.getInstance(project)

            val editor = fileEditorManager.openTextEditor(
                com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file),
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
    }

    class DirNavigate(val dirName: String) : Command() {
        override fun process() {
            TODO("Not yet implemented")
        }
    }

    class Codegen(val prompt: String) : Command() {
        override fun process() {
            TODO("Not yet implemented")
        }
    }

    class IdeaCommand(val ideaCommand: String) : Command() {
        override fun process() {
            TODO("Not yet implemented")
        }
    }

    class Cancel : Command() {
        override fun process() {
            TODO("Not yet implemented")
        }
    }

    class Approve : Command() {
        override fun process() {
            TODO("Not yet implemented")
        }
    }

    class Stop : Command() {
        override fun process() {
            TODO("Not yet implemented")
        }
    }
}
