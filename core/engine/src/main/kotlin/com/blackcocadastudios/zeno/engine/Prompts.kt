package com.blackcocadastudios.zeno.engine

import java.util.concurrent.atomic.AtomicBoolean

/**
 * A question web content asks the user (alert, confirm, file chooser…).
 * Exactly one of the completion methods takes effect; later calls are ignored.
 */
sealed class PromptRequest {
    private val done = AtomicBoolean(false)

    /** True once answered or dismissed. */
    val isComplete: Boolean get() = done.get()

    /** Runs [block] only for the first completion. */
    protected fun complete(block: () -> Unit) {
        if (done.compareAndSet(false, true)) block()
    }

    abstract fun dismiss()

    class Alert(
        val title: String?,
        val message: String,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        fun confirm() = complete(onDismiss)
        override fun dismiss() = complete(onDismiss)
    }

    class Confirm(
        val title: String?,
        val message: String,
        private val onResult: (Boolean) -> Unit,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        fun confirm(accept: Boolean) = complete { onResult(accept) }
        override fun dismiss() = complete(onDismiss)
    }

    class TextInput(
        val title: String?,
        val message: String?,
        val defaultValue: String,
        private val onResult: (String) -> Unit,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        fun confirm(text: String) = complete { onResult(text) }
        override fun dismiss() = complete(onDismiss)
    }

    /** HTTP authentication. */
    class Auth(
        val title: String?,
        val message: String?,
        val origin: String?,
        val username: String,
        val password: String,
        val onlyPassword: Boolean,
        val previousFailed: Boolean,
        private val onResult: (username: String, password: String) -> Unit,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        fun confirm(username: String, password: String) = complete { onResult(username, password) }
        override fun dismiss() = complete(onDismiss)
    }

    /** `<select>` menus and similar choices. */
    class Choice(
        val title: String?,
        val mode: ChoiceMode,
        val items: List<ChoiceItem>,
        private val onResult: (List<String>) -> Unit,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        fun confirm(selectedIds: List<String>) = complete { onResult(selectedIds) }
        override fun dismiss() = complete(onDismiss)
    }

    class Color(
        val defaultColor: String,
        private val onResult: (String) -> Unit,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        fun confirm(color: String) = complete { onResult(color) }
        override fun dismiss() = complete(onDismiss)
    }

    class DateTime(
        val kind: DateTimeKind,
        val value: String?,
        val min: String?,
        val max: String?,
        private val onResult: (String) -> Unit,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        fun confirm(value: String) = complete { onResult(value) }
        override fun dismiss() = complete(onDismiss)
    }

    class File(
        val mimeTypes: List<String>,
        val multiple: Boolean,
        val capture: FileCapture,
        private val onResult: (List<String>) -> Unit,
        private val onDismiss: () -> Unit,
    ) : PromptRequest() {
        /** [uris] are `content://` or `file://` URIs chosen by the user. */
        fun confirm(uris: List<String>) = complete { onResult(uris) }
        override fun dismiss() = complete(onDismiss)
    }

    class Share(
        val title: String?,
        val text: String?,
        val url: String?,
        private val onResult: (ShareResult) -> Unit,
    ) : PromptRequest() {
        fun finish(result: ShareResult) = complete { onResult(result) }
        override fun dismiss() = complete { onResult(ShareResult.Aborted) }
    }

    /** Page wants to show a popup that the engine blocked. */
    class Popup(
        val targetUrl: String?,
        private val onResult: (Boolean) -> Unit,
    ) : PromptRequest() {
        fun confirm(allow: Boolean) = complete { onResult(allow) }
        override fun dismiss() = complete { onResult(false) }
    }

    /** "Leave this page? Changes may not be saved." */
    class BeforeUnload(
        private val onResult: (Boolean) -> Unit,
    ) : PromptRequest() {
        fun confirm(leave: Boolean) = complete { onResult(leave) }
        override fun dismiss() = complete { onResult(false) }
    }

    /** Re-sending form data on reload. */
    class Repost(
        private val onResult: (Boolean) -> Unit,
    ) : PromptRequest() {
        fun confirm(resend: Boolean) = complete { onResult(resend) }
        override fun dismiss() = complete { onResult(false) }
    }
}

data class ChoiceItem(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val isGroup: Boolean = false,
    val isSeparator: Boolean = false,
    val children: List<ChoiceItem> = emptyList(),
)

enum class ChoiceMode { Single, Multiple, Menu }

enum class DateTimeKind { Date, Month, Week, Time, DateTime }

enum class FileCapture { None, Any, User, Environment }

enum class ShareResult { Success, Failure, Aborted }
