package com.blackcocadastudios.zeno.engine.gecko

import android.util.Log
import com.blackcocadastudios.zeno.engine.Login
import kotlinx.coroutines.launch
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult

/**
 * Gecko's login manager asks here for saved logins (to fill forms and offer
 * them on sign-in fields) and hands over the ones the user chose to save.
 * The app's [com.blackcocadastudios.zeno.engine.LoginStore] keeps them.
 */
internal class GeckoLoginStorage(private val engine: GeckoEngine) : Autocomplete.StorageDelegate {

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>>? =
        fetch { store -> store.forDomain(domain) }

    override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>>? =
        fetch { store -> store.all() }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        val store = engine.loginStore ?: return
        engine.scope.launch {
            runCatching { store.save(login.toLogin()) }.onFailure { Log.w(TAG, "Saving a login failed", it) }
        }
    }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        val store = engine.loginStore ?: return
        engine.scope.launch {
            runCatching { store.used(login.toLogin()) }.onFailure { Log.w(TAG, "Marking a login used failed", it) }
        }
    }

    private fun fetch(query: suspend (com.blackcocadastudios.zeno.engine.LoginStore) -> List<Login>): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val store = engine.loginStore ?: return GeckoResult.fromValue(emptyArray())
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        engine.scope.launch {
            val logins = runCatching { query(store) }
                .onFailure { Log.w(TAG, "Reading saved logins failed", it) }
                .getOrDefault(emptyList())
            result.complete(logins.map { it.toEntry() }.toTypedArray())
        }
        return result
    }

    private companion object {
        const val TAG = "Zeno"
    }
}

internal fun Autocomplete.LoginEntry.toLogin() = Login(
    guid = guid,
    origin = origin,
    formActionOrigin = formActionOrigin,
    httpRealm = httpRealm,
    username = username.orEmpty(),
    password = password.orEmpty(),
)

internal fun Login.toEntry(): Autocomplete.LoginEntry = Autocomplete.LoginEntry.Builder()
    .guid(guid)
    .origin(origin)
    .formActionOrigin(formActionOrigin)
    .httpRealm(httpRealm)
    .username(username)
    .password(password)
    .build()
