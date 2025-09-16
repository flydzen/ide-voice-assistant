package com.github.flydzen.idevoiceassistant.startup

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.maddyhome.idea.vim.VimPlugin

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        VimPlugin.setEnabled(false)
        thisLogger().warn("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")
    }
}