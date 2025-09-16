package com.github.flydzen.idevoiceassistant.toolWindow

import com.github.flydzen.idevoiceassistant.AssistantBundle
import com.github.flydzen.idevoiceassistant.services.VoiceRecognitionService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

class VoiceAssistantToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val voiceAssistantTW = VoiceAssistantToolWindow(project)
        val content = ContentFactory.getInstance().createContent(voiceAssistantTW.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class VoiceAssistantToolWindow(private val project: Project) {
        private var _active = false
        private var buttonAnimationTimer: Timer? = null
        private var currentFrame = 0

        private var textTimer: Timer? = null
        private var currentText = ""
        private var displayedCharCount = 0

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        private lateinit var recognizedTextArea: JTextArea


        init {
            val voiceRecognitionService = VoiceRecognitionService.getInstance(project)

            scope.launch {
                voiceRecognitionService.recognizedText.collectLatest { text ->
                    appendRecognizedText(text)
                }
            }

            scope.launch {
                voiceRecognitionService.isRecognitionActive.collectLatest { isActive ->
                    if (!isActive) {
                        stopTextAnimation()
                    }
                }
            }
        }

        private val jetbrainsMonoFont = Font("JetBrains Mono", Font.PLAIN, 15)
        private val jetbrainsMonoBoldFont = Font("JetBrains Mono", Font.BOLD, 30)
        private val jetbrainsMonoUserFont = Font("JetBrains Mono", Font.PLAIN, 12)

        fun getContent() = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            val mainPanel = JBPanel<JBPanel<*>>().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)

                val titleLabel = JBLabel(AssistantBundle.message("assistant.toolwindow.header")).apply {
                    font = jetbrainsMonoBoldFont
                    foreground = JBColor(0xFFFFFF, 0x808080)
                    horizontalAlignment = SwingConstants.LEFT
                    alignmentX = LEFT_ALIGNMENT
                }

                val descriptionLabel = JBLabel().apply {
                    text = "<html><div style='color: white; line-height: 1.4;'>" +
                            "Activate voice commands to control your IDE. Speak " +
                            "naturally to navigate files, " +
                            "insert text, and execute actions." +
                            "</div></html>"
                    font = jetbrainsMonoFont
                    horizontalAlignment = SwingConstants.LEFT
                    alignmentX = LEFT_ALIGNMENT
                }

                val startButton = JButton().apply {
                    font = jetbrainsMonoFont
                    icon = VOICE_ASSISTANT_ICON
                    alignmentX = LEFT_ALIGNMENT
                    preferredSize = Dimension(40, 40)
                    minimumSize = Dimension(40, 40)
                    maximumSize = Dimension(40, 40)

                    addActionListener {
                        _active = !_active
                        if (_active) {
                            clearRecognizedText()
                            startAnimation(this)
                            toolTipText = "Stop listening"
                            VoiceRecognitionService.getInstance(project).startRecognition()
                        } else {
                            toolTipText = "Start listening"
                            stopAnimation()
                            icon = VOICE_ASSISTANT_ICON
                            VoiceRecognitionService.getInstance(project).stopRecognition()
                        }
                    }
                }

                recognizedTextArea = JTextArea().apply {
                    font = jetbrainsMonoUserFont
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    isEditable = false
                    isFocusable = false
                    rows = 0
                    isOpaque = false
                    alignmentX = LEFT_ALIGNMENT
                    foreground = JBColor(0xFFFFFF, 0x808080)
                }

                add(titleLabel)
                add(Box.createVerticalStrut(30))
                add(descriptionLabel)
                add(Box.createVerticalStrut(30))
                add(startButton)
                add(Box.createVerticalStrut(15))
                add(recognizedTextArea)
                add(Box.createVerticalGlue())
            }
            border = BorderFactory.createEmptyBorder(10, 16, 8, 16)
            add(mainPanel, BorderLayout.NORTH)

        }

        // microphone animation
        private fun startAnimation(button: JButton) {
            buttonAnimationTimer = Timer(200) {
                currentFrame = (currentFrame + 1) % ACTIVE_ICONS.size
                button.icon = ACTIVE_ICONS[currentFrame]
            }.apply { start() }
        }

        private fun stopAnimation() {
            buttonAnimationTimer?.stop()
            buttonAnimationTimer = null
            currentFrame = 0
        }

        // text animation
        private fun clearRecognizedText() {
            currentText = ""
            displayedCharCount = 0
            recognizedTextArea.text = ""
        }

        fun appendRecognizedText(additionalText: String) {
            val previousLength = currentText.length
            currentText += additionalText

            if (textTimer?.isRunning == true) {
                displayedCharCount = previousLength
            } else {
                displayedCharCount = previousLength
                startTextAnimation()
            }
        }

        private fun stopTextAnimation() {
            textTimer?.stop()
            textTimer = null
        }

        private fun startTextAnimation() {
            stopTextAnimation()

            textTimer = Timer(30) {
                if (displayedCharCount < currentText.length) {
                    displayedCharCount++
                    val displayText = currentText.substring(0, displayedCharCount)
                    SwingUtilities.invokeLater {
                        recognizedTextArea.text = displayText
                        recognizedTextArea.caretPosition = displayText.length
                    }
                } else {
                    stopTextAnimation()
                }
            }.apply { start() }
        }


        companion object {
            private val ACTIVE_ICONS = listOf(
                IconLoader.getIcon("/icons/voiceAssistantActive1.svg", VoiceAssistantToolWindowFactory::class.java),
                IconLoader.getIcon("/icons/voiceAssistantActive2.svg", VoiceAssistantToolWindowFactory::class.java),
                IconLoader.getIcon("/icons/voiceAssistantActive3.svg", VoiceAssistantToolWindowFactory::class.java),
            )

            private val VOICE_ASSISTANT_ICON =
                IconLoader.getIcon("/icons/voiceAssistant.svg", VoiceAssistantToolWindowFactory::class.java)
        }
    }
}
