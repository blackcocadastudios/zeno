package com.blackcocadastudios.zeno

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.blackcocadastudios.zeno.download.DownloadEvent
import com.blackcocadastudios.zeno.engine.gecko.GeckoEngineView
import com.blackcocadastudios.zeno.ui.AppHost
import com.blackcocadastudios.zeno.ui.MainViewModel
import com.blackcocadastudios.zeno.ui.ZenoApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Zeno's only activity: hosts the interface and receives links from the system. */
class MainActivity : ComponentActivity(), AppHost {

    private val container: AppContainer get() = (application as ZenoApplication).container
    private val vm: MainViewModel by viewModels { MainViewModel.Factory(container) }
    private lateinit var engineView: GeckoEngineView

    private var askedForNotifications = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val browserRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The splash stays until the last session is back (usually a few frames), never for long.
        val start = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { !container.ready.value && SystemClock.uptimeMillis() - start < SPLASH_MAX_MILLIS }
        setSystemBarsDark(resources.configuration.isNight())

        engineView = GeckoEngineView(this)
        setContent { ZenoApp(vm, engineView, this) }

        if (savedInstanceState == null && !IntentProcessor.isReplay(intent)) handle(intent)
        // Gecko starts after the first frame: Zeno's home page is native and needs no engine.
        window.decorView.post { container.engine.warmUp() }
        askForNotificationsOnFirstDownload()
        publishShortcuts()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        container.controller.setAppVisible(true)
    }

    override fun onStop() {
        super.onStop()
        container.controller.setAppVisible(false)
        container.controller.suspendInactiveTabs(INACTIVE_TAB_MILLIS)
        container.saveSessionNow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        container.engine.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
        engineView.release()
    }

    @Suppress("DEPRECATION") // RUNNING_LOW/CRITICAL are still sent on older Android versions.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val tight = level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (tight) container.controller.suspendAllBackgroundTabs()
    }

    private fun handle(intent: Intent) {
        lifecycleScope.launch {
            container.awaitReady()
            when (val action = IntentProcessor.parse(intent, container.searchEngine)) {
                is IntentAction.Open -> vm.openExternal(action.url)
                is IntentAction.NewTab -> vm.newTab(action.private)
                IntentAction.None -> Unit
            }
        }
    }

    override fun setSystemBarsDark(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            // Before Android 8.1 navigation icons cannot be dark: keep a scrim behind them there.
            SystemBarStyle.light(Color.TRANSPARENT, LEGACY_DARK_SCRIM)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    override fun makeDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                if (roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                    vm.message(getString(R.string.settings_already_default))
                    return
                }
                val launched = runCatching { browserRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) }.isSuccess
                if (launched) return
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    /** Download progress lives in a notification; ask for it the first time it matters, once per run. */
    private fun askForNotificationsOnFirstDownload() {
        if (Build.VERSION.SDK_INT < 33) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.downloadCoordinator.events.collect { event ->
                    if (event !is DownloadEvent.Started || askedForNotifications) return@collect
                    val granted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        askedForNotifications = true
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    /** Long-press on the launcher icon: new tab, new private tab. */
    private fun publishShortcuts() {
        val context = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            fun shortcut(id: String, short: Int, long: Int, icon: Int, action: String) =
                ShortcutInfoCompat.Builder(context, id)
                    .setShortLabel(context.getString(short))
                    .setLongLabel(context.getString(long))
                    .setIcon(IconCompat.createWithResource(context, icon))
                    .setIntent(Intent(action).setClass(context, MainActivity::class.java))
                    .build()
            val shortcuts = listOf(
                shortcut("new_tab", R.string.shortcut_new_tab_short, R.string.tabs_new, R.drawable.ic_shortcut_new_tab, IntentProcessor.ACTION_NEW_TAB),
                shortcut("new_private_tab", R.string.shortcut_private_short, R.string.tabs_new_private, R.drawable.ic_shortcut_private, IntentProcessor.ACTION_NEW_PRIVATE_TAB),
            )
            runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
        }
    }

    private fun Configuration.isNight() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val SPLASH_MAX_MILLIS = 1_500L

        /** Background tabs untouched this long are suspended when Zeno leaves the screen. */
        const val INACTIVE_TAB_MILLIS = 30 * 60 * 1000L

        val LEGACY_DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
