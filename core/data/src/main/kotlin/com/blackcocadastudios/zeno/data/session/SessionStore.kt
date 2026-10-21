package com.blackcocadastudios.zeno.data.session

import android.util.AtomicFile
import com.blackcocadastudios.zeno.browser.session.SessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves the open tabs to disk atomically, so a crash mid-write never loses the session. */
class SessionStore(directory: File) {
    private val file = AtomicFile(File(directory, "session.json"))

    suspend fun read(): SessionSnapshot? = withContext(Dispatchers.IO) {
        runCatching { file.readFully().toString(Charsets.UTF_8) }
            .getOrNull()
            ?.let(SessionSnapshot::decode)
    }

    suspend fun write(snapshot: SessionSnapshot) = withContext(Dispatchers.IO) {
        val bytes = snapshot.encode().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
