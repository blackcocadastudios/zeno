package com.blackcocadastudios.zeno.engine

/** A username and password for a site's sign-in form (or its HTTP authentication). */
data class Login(
    /** Identifies a saved login; null for one not saved yet. */
    val guid: String? = null,
    /** Where it is used: scheme, host and port, e.g. `https://accounts.example.com`. */
    val origin: String,
    /** Where the form sends it; null for HTTP authentication. */
    val formActionOrigin: String? = null,
    /** The HTTP authentication realm; null for a form. */
    val httpRealm: String? = null,
    val username: String,
    val password: String,
)

/** Where saved logins live. The app provides it; the engine fills forms from it. */
interface LoginStore {
    /** Logins for [baseDomain] (`example.com`) and its subdomains; the engine picks those that fit the page. */
    suspend fun forDomain(baseDomain: String): List<Login>

    suspend fun all(): List<Login>

    /** Adds [login], or replaces the saved one it updates (same guid, or same site and username). */
    suspend fun save(login: Login)

    /** [login] was just used to sign in. */
    suspend fun used(login: Login)
}

/** A saved login offered for a sign-in field, or a strong password the engine made up. */
data class LoginChoice(
    val login: Login,
    /** A new password generated for a sign-up form, not a saved login. */
    val generated: Boolean = false,
)
