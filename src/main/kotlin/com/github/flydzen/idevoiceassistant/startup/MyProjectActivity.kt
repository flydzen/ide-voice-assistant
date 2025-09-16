package com.github.flydzen.idevoiceassistant.startup

import com.github.flydzen.idevoiceassistant.codeGeneration.AutoCodeGenerationListener
import com.intellij.ml.llm.codeGeneration.AICodeGenerationListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.messageBus.connect()
            .subscribe(AICodeGenerationListener.TOPIC, AutoCodeGenerationListener(project))
    }
}