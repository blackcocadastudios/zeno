package com.blackcocadastudios.zeno

import android.app.Application

class ZenoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Gecko's child processes run this Application too; they must stay inert.
        if (!isMainProcess()) return
        container = AppContainer(this)
        container.start()
    }

    private fun isMainProcess(): Boolean = currentProcessName() == packageName

    private fun currentProcessName(): String =
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            runCatching {
                java.io.File("/proc/self/cmdline").readText().trim { it <= ' ' || it == '\u0000' }
            }.getOrDefault(packageName)
        }
}
