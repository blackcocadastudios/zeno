package com.blackcocadastudios.zeno.engine.gecko

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Suspends until the GeckoResult completes. Cancelling the coroutine cancels the result. */
internal suspend fun <T> GeckoResult<T>.await(): T = suspendCancellableCoroutine { cont ->
    accept(
        { value ->
            @Suppress("UNCHECKED_CAST")
            cont.resume(value as T)
        },
        { error -> cont.resumeWithException(error ?: IllegalStateException("GeckoResult failed")) },
    )
    cont.invokeOnCancellation { cancel() }
}
