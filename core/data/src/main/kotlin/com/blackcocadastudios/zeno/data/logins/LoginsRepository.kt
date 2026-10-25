package com.blackcocadastudios.zeno.data.logins

import com.blackcocadastudios.zeno.data.db.LoginDao
import com.blackcocadastudios.zeno.data.db.LoginEntity
import com.blackcocadastudios.zeno.data.db.LoginExceptionEntity
import com.blackcocadastudios.zeno.engine.Login
import com.blackcocadastudios.zeno.engine.LoginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID

/** A saved login as the settings list it: the password stays encrypted until asked for. */
data class SavedLogin(
    val guid: String,
    val origin: String,
    val username: String,
    val httpRealm: String?,
    val createdAt: Long,
    val lastUsedAt: Long,
    val timesUsed: Int,
)

/**
 * Saved logins, encrypted on disk. The engine fills and saves through
 * [LoginStore]; the settings list, show, change and delete them.
 */
class LoginsRepository(
    private val dao: LoginDao,
    private val cipher: SecretCipher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newGuid: () -> String = { UUID.randomUUID().toString() },
) : LoginStore {

    /** Every saved login, by site. Ones that cannot be read (key lost) are left out. */
    val saved: Flow<List<SavedLogin>> = dao.all()
        .map { list -> list.mapNotNull { it.summary() } }
        .flowOn(Dispatchers.Default)

    /** Sites where saving is never offered. */
    val neverSave: Flow<List<String>> = dao.exceptions().map { list -> list.map { it.origin } }

    override suspend fun forDomain(baseDomain: String): List<Login> = withContext(Dispatchers.IO) {
        val domain = baseDomain.lowercase()
        dao.hostsEndingWith("%" + escapeLike(domain))
            .filter { it.host == domain || it.host.endsWith(".$domain") }
            .mapNotNull { it.toLogin() }
    }

    override suspend fun all(): List<Login> = withContext(Dispatchers.IO) { dao.allNow().mapNotNull { it.toLogin() } }

    override suspend fun save(login: Login) = withContext(Dispatchers.IO) {
        val host = hostOf(login.origin) ?: return@withContext
        val now = clock()
        val existing = login.guid?.let { dao.get(it) }
            ?: dao.forHost(host).firstOrNull { sameSlot(it, login) }
        val entity = if (existing != null) {
            val changed = cipher.decrypt(existing.encryptedPassword) != login.password
            existing.copy(
                origin = login.origin,
                host = host,
                formActionOrigin = login.formActionOrigin ?: existing.formActionOrigin,
                httpRealm = login.httpRealm,
                encryptedUsername = cipher.encrypt(login.username),
                encryptedPassword = cipher.encrypt(login.password),
                passwordChangedAt = if (changed) now else existing.passwordChangedAt,
            )
        } else {
            LoginEntity(
                guid = login.guid ?: newGuid(),
                origin = login.origin,
                host = host,
                formActionOrigin = login.formActionOrigin,
                httpRealm = login.httpRealm,
                encryptedUsername = cipher.encrypt(login.username),
                encryptedPassword = cipher.encrypt(login.password),
                createdAt = now,
                lastUsedAt = now,
                passwordChangedAt = now,
                timesUsed = 0,
            )
        }
        dao.upsert(entity)
    }

    override suspend fun used(login: Login) {
        val guid = login.guid ?: return
        withContext(Dispatchers.IO) { dao.markUsed(guid, clock()) }
    }

    /** The password of a saved login, decrypted only now. */
    suspend fun password(guid: String): String? = withContext(Dispatchers.IO) {
        dao.get(guid)?.let { cipher.decrypt(it.encryptedPassword) }
    }

    suspend fun update(guid: String, username: String, password: String) = withContext(Dispatchers.IO) {
        val existing = dao.get(guid) ?: return@withContext
        val changed = cipher.decrypt(existing.encryptedPassword) != password
        dao.upsert(
            existing.copy(
                encryptedUsername = cipher.encrypt(username),
                encryptedPassword = cipher.encrypt(password),
                passwordChangedAt = if (changed) clock() else existing.passwordChangedAt,
            ),
        )
    }

    suspend fun delete(guid: String) = withContext(Dispatchers.IO) { dao.delete(guid) }

    suspend fun isNeverSave(origin: String): Boolean = withContext(Dispatchers.IO) { dao.isException(origin) }

    suspend fun neverSave(origin: String) = withContext(Dispatchers.IO) { dao.addException(LoginExceptionEntity(origin, clock())) }

    suspend fun allowSaving(origin: String) = withContext(Dispatchers.IO) { dao.removeException(origin) }

    /** Same site, same kind of login (form or HTTP realm), same username: an update, not a new login. */
    private fun sameSlot(entity: LoginEntity, login: Login): Boolean =
        entity.origin == login.origin &&
            entity.httpRealm == login.httpRealm &&
            cipher.decrypt(entity.encryptedUsername) == login.username

    private fun LoginEntity.toLogin(): Login? {
        val username = cipher.decrypt(encryptedUsername) ?: return null
        val password = cipher.decrypt(encryptedPassword) ?: return null
        return Login(guid, origin, formActionOrigin, httpRealm, username, password)
    }

    private fun LoginEntity.summary(): SavedLogin? {
        val username = cipher.decrypt(encryptedUsername) ?: return null
        return SavedLogin(guid, origin, username, httpRealm, createdAt, lastUsedAt, timesUsed)
    }

    private companion object {
        fun hostOf(origin: String): String? = runCatching { URI(origin).host?.lowercase() }.getOrNull()

        fun escapeLike(text: String) = buildString {
            for (c in text) {
                if (c == '%' || c == '_' || c == '\\') append('\\')
                append(c)
            }
        }
    }
}
