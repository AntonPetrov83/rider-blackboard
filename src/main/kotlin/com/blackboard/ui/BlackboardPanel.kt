package com.blackboard.ui

import com.blackboard.BlackboardBundle
import com.blackboard.services.BlackboardService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.DocumentUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * Main Blackboard UI: a Markdown editor bound to [BlackboardService] and a status line at the bottom.
 *
 * The editor is read-only until the service has loaded the note; the text is then taken from the service's
 * memory (not from the file), so reopening the tool window always shows the latest edits.
 */
class BlackboardPanel(project: Project) : JBPanel<BlackboardPanel>(BorderLayout()), Disposable {

    private val service = BlackboardService.getInstance(project)
    private val scope: CoroutineScope = service.createChildScope("BlackboardPanel")

    private val document = EditorFactory.getInstance().createDocument("")
    private val editor: EditorEx =
        EditorFactory.getInstance().createEditor(document, project, markdownFileType(), false) as EditorEx

    /** True while the panel itself replaces the document text; such changes are not user edits. */
    private var applyingExternalText = false

    private val statusLabel = JBLabel().apply {
        font = JBFont.small()
        border = JBUI.Borders.empty(2, 8)
        accessibleContext.accessibleName = BlackboardBundle.message("status.accessible.name")
    }

    val preferredFocusComponent: JComponent
        get() = editor.contentComponent

    init {
        // Not `accessibleContext`: inside a JComponent subclass it resolves to the protected field, which is null
        // until the getter lazily creates the context.
        getAccessibleContext().accessibleName = BlackboardBundle.message("panel.accessible.name")
        configureEditor()
        add(editor.component, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (!applyingExternalText) service.onTextChanged(document.text)
            }
        }, this)

        scope.launch(Dispatchers.EDT) {
            // Load errors and retries are surfaced by the status line; the text is applied once loading succeeds.
            service.status.first { service.isLoaded }
            onServiceLoaded()
        }
    }

    private fun configureEditor() {
        editor.isViewer = true
        editor.setBorder(JBUI.Borders.empty())
        editor.contentComponent.accessibleContext.accessibleName = BlackboardBundle.message("panel.accessible.name")
        with(editor.settings) {
            isUseSoftWraps = true
            isLineNumbersShown = false
            isFoldingOutlineShown = false
            isLineMarkerAreaShown = false
            isAdditionalPageAtBottom = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
            isRightMarginShown = false
            isIndentGuidesShown = false
        }
    }

    private fun onServiceLoaded() {
        applyText(service.currentText())
        editor.isViewer = false
        // Set only after loading, so the placeholder never shows over a not-yet-read non-empty note.
        editor.setPlaceholder(BlackboardBundle.message("editor.placeholder"))
        editor.setShowPlaceholderWhenFocused(true)
    }

    /** Replaces the document text without an undo entry and without reporting it as a user edit. */
    private fun applyText(text: String) {
        applyingExternalText = true
        try {
            DocumentUtil.writeInRunUndoTransparentAction {
                document.setText(StringUtil.convertLineSeparators(text))
            }
        } finally {
            applyingExternalText = false
        }
    }

    override fun dispose() {
        scope.cancel()
        EditorFactory.getInstance().releaseEditor(editor)
    }

    private companion object {
        /** Markdown if the bundled Markdown plugin is available, plain text otherwise (no hard dependency). */
        fun markdownFileType(): FileType {
            val type = FileTypeManager.getInstance().getFileTypeByExtension("md")
            return if (type is UnknownFileType) PlainTextFileType.INSTANCE else type
        }
    }
}
