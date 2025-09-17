package com.github.flydzen.idevoiceassistant.commands

import com.github.flydzen.idevoiceassistant.Utils.editor
import com.github.flydzen.idevoiceassistant.openai.Parameter
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

class CreateFileCommand private constructor(
    private val project: Project,
    private val path: String
) : Command() {

    override fun process() {
        invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    val baseDir = resolveBaseDirectory() ?: run {
                        LOG.warn("CreateFile: base directory not resolved")
                        return@runWriteCommandAction
                    }

                    val parts = path.replace('\\', '/').trim('/').split('/').filter { it.isNotBlank() }
                    if (parts.isEmpty()) {
                        LOG.warn("CreateFile: empty path")
                        return@runWriteCommandAction
                    }
                    val fileName = parts.last()
                    val parent = ensureSubdirs(baseDir, parts.dropLast(1))

                    val file = parent.createChildData(this, fileName)
                    FileEditorManager.getInstance(project).openFile(file, true)
                    LOG.info("CreateFile: created ${file.path}")
                } catch (t: Throwable) {
                    LOG.warn("CreateFile failed for '$path': ${t.message}", t)
                }
            }
        }
    }

    private fun resolveBaseDirectory(): VirtualFile? {
        // 1) Папка текущего открытого файла (если есть)
        project.editor()?.document?.let { doc ->
            val file = FileDocumentManager.getInstance().getFile(doc)
            file?.parent?.let { return it }
        }
        // 2) Иначе — корень проекта
        return project.basePath?.let { VfsUtil.createDirectories(it) }
    }

    private fun ensureSubdirs(root: VirtualFile, subdirs: List<String>): VirtualFile {
        var current = root
        for (seg in subdirs) {
            current = current.findChild(seg) ?: current.createChildDirectory(this, seg)
        }
        return current
    }

    override fun rollback() {
        TODO("Not yet implemented")
    }

    override fun toString(): String = "CreateFile(path=\"$path\")"

    companion object : CommandMeta {
        private val LOG = thisLogger()

        override val toolName: String = "createFile"
        override val description: String = "Create new file"
        override val parameters: List<Parameter> = listOf(
            Parameter("path", "string", "File name or file path to create. (e.g., MyClass.kt or src/main/kotlin/MyClass.kt)")
        )

        override fun build(project: Project, previousCommand: Command?, params: Map<String, Any>): Command = build(project, params)

        fun build(project: Project, params: Map<String, Any>): CreateFileCommand {
            val path = params["path"] as String
            return CreateFileCommand(project, path)
        }
    }
}