package com.edgegesture.evilgodxu

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.edgegesture.evilgodxu.data.gesture.gestureSettingsFlow
import com.edgegesture.evilgodxu.navigation.NavGraph
import com.edgegesture.evilgodxu.screens.settings.appLanguageFlow
import com.edgegesture.evilgodxu.ui.adaptive.ProvideWindowSizeClass
import com.edgegesture.evilgodxu.ui.theme.MyApplicationTheme
import com.edgegesture.evilgodxu.update.UpdateCheckWorker
import com.edgegesture.evilgodxu.update.UpdateDialog
import com.edgegesture.evilgodxu.update.UpdateManager
import com.edgegesture.evilgodxu.update.UpdateViewModel
import com.edgegesture.evilgodxu.utils.localization.LocalizationManager
import com.edgegesture.evilgodxu.utils.localization.ProvideLocalizedContext
import com.edgegesture.evilgodxu.utils.localization.toLocale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private val localizationManager: LocalizationManager by inject()

    // 冷启动按持久化语言创建配置上下文，进入界面即正确语言
    override fun attachBaseContext(newBase: Context) {
        val locale = runBlocking { newBase.appLanguageFlow().first() }.toLocale()
        val config = Configuration(newBase.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (handleExternalAudioIntent(intent)) {
            finish()
            return
        }
        enableEdgeToEdge()

        // 绑定当前 Activity，使对话框等独立窗口在切语言时同步更新资源
        localizationManager.bindActivity(this)

        // 设置系统栏控制
        setupSystemBars()

        // 监听生命周期，更新前台状态
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> UpdateCheckWorker.isAppInForeground = true
                Lifecycle.Event.ON_STOP -> UpdateCheckWorker.isAppInForeground = false
                else -> {}
            }
        })

        setContent {
            ProvideLocalizedContext(localizationManager) {
                ProvideWindowSizeClass {
                    MyApplicationTheme {
                        val updateViewModel: UpdateViewModel = koinViewModel()

                        // 从通知打开时检查是否携带 show_update 标记
                        LaunchedEffect(Unit) {
                            if (intent?.getBooleanExtra("show_update", false) == true) {
                                updateViewModel.checkForUpdate(force = true)
                            }
                        }

                        // 回到前台时检查是否有待更新
                        // 协程由 ViewModel 的 viewModelScope 管理，无需手动创建作用域
                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    updateViewModel.checkForUpdate()
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        NavGraph()

                        // 更新对话框
                        val updateInfo by updateViewModel.updateInfo.collectAsStateWithLifecycle()
                        val showUpdateDialog by updateViewModel.showUpdateDialog.collectAsStateWithLifecycle()
                        val downloadState by updateViewModel.downloadState.collectAsStateWithLifecycle()

                        if (showUpdateDialog && updateInfo != null) {
                            // 委托属性无法智能转换，先解包为局部变量再判空
                            val info = updateInfo
                            if (info != null) {
                                UpdateDialog(
                                    updateInfo = info,
                                    downloadState = downloadState,
                                    onDownload = { updateViewModel.downloadAndInstall() },
                                    onOpenBrowser = {
                                        val url = UpdateManager.GITHUB_REPOSITORY_URL
                                        if (url.startsWith("http")) {
                                            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                        }
                                        updateViewModel.dismissUpdateDialog()
                                    },
                                    onDismiss = { updateViewModel.dismissUpdateDialog() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupSystemBars() {
        windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        updateSystemBarsVisibility()
    }

    private fun updateSystemBarsVisibility() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun handleExternalAudioIntent(intent: android.content.Intent?): Boolean {
        val uri = when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.data
            android.content.Intent.ACTION_SEND -> intent.getParcelableExtra(
                android.content.Intent.EXTRA_STREAM,
                android.net.Uri::class.java
            )
            else -> null
        } ?: return false

        return com.edgegesture.evilgodxu.screens.gesture.service.EdgeGestureAccessibilityService
            .handleExternalAudioIntent(uri)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (handleExternalAudioIntent(intent)) {
            finish()
            return
        }
        setIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBarsVisibility()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台时同步隐藏后台设置，确保设置变更即时生效
        lifecycleScope.launch {
            val settings = gestureSettingsFlow().first()
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { task ->
                task.setExcludeFromRecents(settings.hideFromRecents)
            }
        }
    }
}
