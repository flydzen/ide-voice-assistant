package com.github.flydzen.idevoiceassistant.services

import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.injector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class VimScriptExecutionService(private val project: Project, private val scope: CoroutineScope) {
    /**
     * Executes the specified Vim script and returns the result of the execution.
     *
     * @param vimScript the Vim script to be executed
     * @return true if the execution was successful, false otherwise
     */
    @Suppress("UnstableApiUsage")
    fun execute(vimScript: String) = scope.launch {
        writeCommandAction(project, "Vim Script Execution") {
            withVimPlugin {
                val vimEditor = injector.editorGroup.getFocusedEditor()
                if (vimEditor == null) {
                    LOG.error("No focused editor")
                    return@withVimPlugin
                }
                val vimContext = injector.executionContextManager.getEditorExecutionContext(vimEditor)
                injector.vimscriptExecutor.execute(
                    vimScript, vimEditor, vimContext,
                    skipHistory = true,
                    indicateErrors = true
                )
            }
        }
    }

    private fun withVimPlugin(block: () -> Unit) {
        try {
            VimPlugin.setEnabled(true)
            block()
        }
        finally {
            VimPlugin.setEnabled(false)
        }
    }

    companion object {
        private val LOG = logger<VimScriptExecutionService>()

        fun getInstance(project: Project): VimScriptExecutionService = project.service()
    }
}