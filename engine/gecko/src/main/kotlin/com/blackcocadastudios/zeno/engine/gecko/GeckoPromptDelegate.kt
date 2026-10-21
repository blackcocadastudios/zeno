package com.blackcocadastudios.zeno.engine.gecko

import android.content.Context
import android.net.Uri
import com.blackcocadastudios.zeno.engine.ChoiceItem
import com.blackcocadastudios.zeno.engine.ChoiceMode
import com.blackcocadastudios.zeno.engine.DateTimeKind
import com.blackcocadastudios.zeno.engine.EngineSession
import com.blackcocadastudios.zeno.engine.FileCapture
import com.blackcocadastudios.zeno.engine.PromptRequest
import com.blackcocadastudios.zeno.engine.ShareResult
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AlertPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.AuthPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BasePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.BeforeUnloadPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ButtonPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ChoicePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.ColorPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.DateTimePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FilePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.FolderUploadPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PopupPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptInstanceDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse
import org.mozilla.geckoview.GeckoSession.PromptDelegate.RedirectPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.RepostConfirmPrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.SharePrompt
import org.mozilla.geckoview.GeckoSession.PromptDelegate.TextPrompt

/**
 * Turns GeckoView prompts into [PromptRequest]s. Each request completes the
 * GeckoResult exactly once, whether the user answers or Gecko withdraws it.
 */
internal class GeckoPromptDelegate(
    private val context: Context,
    private val observer: () -> EngineSession.Observer?,
) : GeckoSession.PromptDelegate {

    /** Sends [request] to the tab and wires Gecko-side dismissal back to it. */
    private fun <P : BasePrompt> ask(
        prompt: P,
        result: GeckoResult<PromptResponse>,
        request: PromptRequest,
    ): GeckoResult<PromptResponse>? {
        val target = observer()
        if (target == null) {
            result.completeSafely { prompt.dismiss() }
            return result
        }
        prompt.setDelegate(
            object : PromptInstanceDelegate {
                override fun onPromptDismiss(prompt: BasePrompt) {
                    target.onPromptDismissed(request)
                }
            },
        )
        target.onPrompt(request)
        return result
    }

    private fun GeckoResult<PromptResponse>.completeSafely(response: () -> PromptResponse) {
        runCatching { complete(response()) }
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: AlertPrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.Alert(prompt.title, prompt.message.orEmpty()) { result.completeSafely { prompt.dismiss() } },
        )
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: ButtonPrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.Confirm(
                title = prompt.title,
                message = prompt.message.orEmpty(),
                onResult = { ok ->
                    result.completeSafely {
                        prompt.confirm(if (ok) ButtonPrompt.Type.POSITIVE else ButtonPrompt.Type.NEGATIVE)
                    }
                },
                onDismiss = { result.completeSafely { prompt.dismiss() } },
            ),
        )
    }

    override fun onTextPrompt(session: GeckoSession, prompt: TextPrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.TextInput(
                title = prompt.title,
                message = prompt.message,
                defaultValue = prompt.defaultValue.orEmpty(),
                onResult = { text -> result.completeSafely { prompt.confirm(text) } },
                onDismiss = { result.completeSafely { prompt.dismiss() } },
            ),
        )
    }

    override fun onAuthPrompt(session: GeckoSession, prompt: AuthPrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        val options = prompt.authOptions
        val onlyPassword = options.flags and AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD != 0
        return ask(
            prompt,
            result,
            PromptRequest.Auth(
                title = prompt.title,
                message = prompt.message,
                origin = options.uri,
                username = options.username.orEmpty(),
                password = options.password.orEmpty(),
                onlyPassword = onlyPassword,
                previousFailed = options.flags and AuthPrompt.AuthOptions.Flags.PREVIOUS_FAILED != 0,
                onResult = { user, password ->
                    result.completeSafely {
                        if (onlyPassword) prompt.confirm(password) else prompt.confirm(user, password)
                    }
                },
                onDismiss = { result.completeSafely { prompt.dismiss() } },
            ),
        )
    }

    override fun onChoicePrompt(session: GeckoSession, prompt: ChoicePrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        val mode = when (prompt.type) {
            ChoicePrompt.Type.MULTIPLE -> ChoiceMode.Multiple
            ChoicePrompt.Type.MENU -> ChoiceMode.Menu
            else -> ChoiceMode.Single
        }
        return ask(
            prompt,
            result,
            PromptRequest.Choice(
                title = prompt.title ?: prompt.message,
                mode = mode,
                items = prompt.choices.map(::toItem),
                onResult = { ids ->
                    result.completeSafely {
                        when {
                            ids.isEmpty() -> prompt.dismiss()
                            mode == ChoiceMode.Multiple -> prompt.confirm(ids.toTypedArray())
                            else -> prompt.confirm(ids.first())
                        }
                    }
                },
                onDismiss = { result.completeSafely { prompt.dismiss() } },
            ),
        )
    }

    private fun toItem(choice: ChoicePrompt.Choice): ChoiceItem = ChoiceItem(
        id = choice.id,
        label = choice.label,
        enabled = !choice.disabled,
        selected = choice.selected,
        isGroup = choice.items != null,
        isSeparator = choice.separator,
        children = choice.items?.map(::toItem).orEmpty(),
    )

    override fun onColorPrompt(session: GeckoSession, prompt: ColorPrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.Color(
                defaultColor = prompt.defaultValue ?: "#000000",
                onResult = { color -> result.completeSafely { prompt.confirm(color) } },
                onDismiss = { result.completeSafely { prompt.dismiss() } },
            ),
        )
    }

    override fun onDateTimePrompt(session: GeckoSession, prompt: DateTimePrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        val kind = when (prompt.type) {
            DateTimePrompt.Type.MONTH -> DateTimeKind.Month
            DateTimePrompt.Type.WEEK -> DateTimeKind.Week
            DateTimePrompt.Type.TIME -> DateTimeKind.Time
            DateTimePrompt.Type.DATETIME_LOCAL -> DateTimeKind.DateTime
            else -> DateTimeKind.Date
        }
        return ask(
            prompt,
            result,
            PromptRequest.DateTime(
                kind = kind,
                value = prompt.defaultValue,
                min = prompt.minValue,
                max = prompt.maxValue,
                onResult = { value -> result.completeSafely { prompt.confirm(value) } },
                onDismiss = { result.completeSafely { prompt.dismiss() } },
            ),
        )
    }

    override fun onFilePrompt(session: GeckoSession, prompt: FilePrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        val capture = when (prompt.capture) {
            FilePrompt.Capture.ANY -> FileCapture.Any
            FilePrompt.Capture.USER -> FileCapture.User
            FilePrompt.Capture.ENVIRONMENT -> FileCapture.Environment
            else -> FileCapture.None
        }
        return ask(
            prompt,
            result,
            PromptRequest.File(
                mimeTypes = prompt.mimeTypes?.toList().orEmpty(),
                multiple = prompt.type == FilePrompt.Type.MULTIPLE,
                capture = capture,
                onResult = { uris ->
                    result.completeSafely {
                        val parsed = uris.map(Uri::parse)
                        when {
                            parsed.isEmpty() -> prompt.dismiss()
                            prompt.type == FilePrompt.Type.MULTIPLE -> prompt.confirm(context, parsed.toTypedArray())
                            else -> prompt.confirm(context, parsed.first())
                        }
                    }
                },
                onDismiss = { result.completeSafely { prompt.dismiss() } },
            ),
        )
    }

    override fun onPopupPrompt(session: GeckoSession, prompt: PopupPrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.Popup(prompt.targetUri) { allow ->
                result.completeSafely { prompt.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
            },
        )
    }

    override fun onRedirectPrompt(session: GeckoSession, prompt: RedirectPrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.Popup(prompt.targetUri) { allow ->
                result.completeSafely { prompt.confirm(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
            },
        )
    }

    override fun onSharePrompt(session: GeckoSession, prompt: SharePrompt): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.Share(prompt.title, prompt.text, prompt.uri) { outcome ->
                result.completeSafely {
                    when (outcome) {
                        ShareResult.Success -> prompt.confirm(SharePrompt.Result.SUCCESS)
                        ShareResult.Failure -> prompt.confirm(SharePrompt.Result.FAILURE)
                        ShareResult.Aborted -> prompt.dismiss()
                    }
                }
            },
        )
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: BeforeUnloadPrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.BeforeUnload { leave ->
                result.completeSafely { prompt.confirm(if (leave) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
            },
        )
    }

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: RepostConfirmPrompt,
    ): GeckoResult<PromptResponse>? {
        val result = GeckoResult<PromptResponse>()
        return ask(
            prompt,
            result,
            PromptRequest.Repost { resend ->
                result.completeSafely { prompt.confirm(if (resend) AllowOrDeny.ALLOW else AllowOrDeny.DENY) }
            },
        )
    }

    /** Folder uploads ask nothing extra: the user already picked the folder. */
    override fun onFolderUploadPrompt(
        session: GeckoSession,
        prompt: FolderUploadPrompt,
    ): GeckoResult<PromptResponse>? = GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
}
