package com.blackcocadastudios.zeno.data.logins

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.blackcocadastudios.zeno.data.db.LoginEntity
import com.blackcocadastudios.zeno.data.db.ZenoDatabase
import com.blackcocadastudios.zeno.engine.Login
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reversible stand-in for the keystore, which Robolectric does not have. */
private class FakeCipher : SecretCipher {
    var broken = false
    override fun encrypt(plain: String) = "fake:" + plain.reversed()
    override fun decrypt(sealed: String): String? = if (broken || !sealed.startsWith("fake:")) null else sealed.removePrefix("fake:").reversed()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LoginsTest {
    private lateinit var db: ZenoDatabase
    private val cipher = FakeCipher()
    private var now = 1_760_000_000_000L
    private var guids = 0
    private val logins by lazy { LoginsRepository(db.logins(), cipher, clock = { now }, newGuid = { "g${guids++}" }) }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZenoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun login(origin: String = "https://accounts.example.com", user: String = "hana", pass: String = "s3nh4", guid: String? = null) =
        Login(guid = guid, origin = origin, formActionOrigin = origin, username = user, password = pass)

    @Test
    fun `usernames and passwords never reach the disk in the clear`() = runTest {
        logins.save(login())
        val row = db.logins().allNow().single()
        assertFalse("hana" in row.encryptedUsername)
        assertFalse("s3nh4" in row.encryptedPassword)
        assertEquals("accounts.example.com", row.host)
        assertEquals("s3nh4", logins.password(row.guid))
    }

    @Test
    fun `logins are found by base domain, subdomains included, look-alikes not`() = runTest {
        logins.save(login("https://accounts.example.com"))
        logins.save(login("https://example.com", user = "kai"))
        logins.save(login("https://notexample.com", user = "x"))
        logins.save(login("https://example.com.evil.test", user = "y"))

        assertEquals(setOf("hana", "kai"), logins.forDomain("example.com").map { it.username }.toSet())
        assertEquals(listOf("x"), logins.forDomain("notexample.com").map { it.username })
        assertTrue(logins.forDomain("ex_mple.com").isEmpty())
    }

    @Test
    fun `saving again updates the login with the same guid, or the same site and username`() = runTest {
        logins.save(login(pass = "one"))
        val guid = logins.forDomain("example.com").single().guid!!
        now += 1_000
        logins.save(login(pass = "two"))
        assertEquals(listOf("two"), logins.forDomain("example.com").map { it.password })

        logins.save(login(user = "hana.m", pass = "three", guid = guid))
        val saved = logins.forDomain("example.com").single()
        assertEquals("hana.m", saved.username)
        assertEquals("three", saved.password)
        assertEquals(1, db.logins().allNow().size)
    }

    @Test
    fun `use is counted, edits and deletes stick`() = runTest {
        logins.save(login())
        val guid = logins.forDomain("example.com").single().guid!!
        now += 5_000
        logins.used(login(guid = guid))
        val row = db.logins().get(guid)!!
        assertEquals(1, row.timesUsed)
        assertEquals(now, row.lastUsedAt)

        logins.update(guid, "hana", "nova")
        assertEquals("nova", logins.password(guid))
        logins.delete(guid)
        assertTrue(logins.saved.first().isEmpty())
    }

    @Test
    fun `the list shows usernames, not passwords, and skips what cannot be read`() = runTest {
        logins.save(login())
        val listed = logins.saved.first().single()
        assertEquals("hana", listed.username)
        assertEquals("https://accounts.example.com", listed.origin)

        cipher.broken = true
        assertTrue(logins.saved.first().isEmpty())
        assertTrue(logins.forDomain("example.com").isEmpty())
        assertNull(logins.password(listed.guid))
    }

    @Test
    fun `sites can be told never to save`() = runTest {
        assertFalse(logins.isNeverSave("https://bank.example"))
        logins.neverSave("https://bank.example")
        assertTrue(logins.isNeverSave("https://bank.example"))
        assertEquals(listOf("https://bank.example"), logins.neverSave.first())
        logins.allowSaving("https://bank.example")
        assertFalse(logins.isNeverSave("https://bank.example"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Test
    fun `a version 1 database opens as version 2, history and bookmarks intact`() {
        context.deleteDatabase(name)
        createVersion1()
        val db = Room.databaseBuilder(context, ZenoDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            runBlocking {
                assertEquals("A", db.history().get("https://a.example/")?.title)
                assertEquals(listOf("https://b.example/"), db.bookmarks().pinned().first().map { it.url })
                db.logins().upsert(
                    LoginEntity("g", "https://c.example", "c.example", null, null, "u", "p", 1, 1, 1, 0),
                )
                assertEquals(1, db.logins().allNow().size)
            }
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    /** The database exactly as version 1 left it, built from the schema Room exported then. */
    private fun createVersion1() {
        val schema = JSONObject(File(SCHEMA_1).readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace(TABLE, table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE, table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO history VALUES ('https://a.example/', 'A', 'a.example', 3, 1, 2)")
            db.execSQL("INSERT INTO bookmarks (url, title, createdAt, pinned, position) VALUES ('https://b.example/', 'B', 1, 1, 0)")
            db.version = 1
        }
    }

    private companion object {
        /** Tests run in the module's directory. */
        const val SCHEMA_1 = "schemas/com.blackcocadastudios.zeno.data.db.ZenoDatabase/1.json"
        const val TABLE = "\${TABLE_NAME}"
    }
}
