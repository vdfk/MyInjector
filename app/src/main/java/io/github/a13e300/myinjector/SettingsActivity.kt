package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.a13e300.myinjector.system_server.ResultReceiver
import io.github.a13e300.myinjector.ui.ModernActionButton
import io.github.a13e300.myinjector.ui.ModernChevronView
import io.github.a13e300.myinjector.ui.ModernSettingsCard
import io.github.a13e300.myinjector.ui.ModernSettingsHeader
import io.github.a13e300.myinjector.ui.ModernSettingsPalette
import io.github.a13e300.myinjector.ui.ModernSettingsRow
import io.github.a13e300.myinjector.ui.ModernSwitchView
import io.github.a13e300.myinjector.ui.dp
import io.github.a13e300.myinjector.ui.setTextSizeDp
import org.xmlpull.v1.XmlPullParser
import java.util.Arrays
import java.util.UUID
import java.util.stream.Collectors
import kotlin.math.min

class SettingsActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var scrollView: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var header: ModernSettingsHeader
    private lateinit var headerScrim: View
    private lateinit var palette: ModernSettingsPalette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = ModernSettingsPalette.from(this)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        configureWindow()
        setContentView(R.layout.activity_settings)

        root = findViewById(R.id.settings_root)
        root.setBackgroundColor(palette.background)
        buildContent()
    }

    private fun configureWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }

        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (palette.isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (palette.isLight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    private fun buildContent() {
        scrollView = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }

        scrollView.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )
        root.addView(
            scrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        )

        loadSectionsFromPrefsXml().forEach(::addSection)
        buildAppsSection()?.let(::addSection)

        headerScrim = View(this).apply {
            background = palette.headerScrim()
        }
        root.addView(
            headerScrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(124),
                Gravity.TOP,
            )
        )

        header = ModernSettingsHeader(this, palette).apply {
            setTitle(getString(R.string.app_name))
            setOnCloseClickListener { finish() }
        }
        root.addView(
            header,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78),
                Gravity.TOP,
            )
        )

        root.setOnApplyWindowInsetsListener { _, windowInsets ->
            applyInsets(windowInsets)
        }
        root.requestApplyInsets()

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val progress = min(1f, scrollY / dp(72).toFloat())
            header.setScrollProgress(progress)
            headerScrim.alpha = 0.72f + 0.28f * progress
        }
    }

    @Suppress("DEPRECATION")
    private fun applyInsets(windowInsets: WindowInsets): WindowInsets {
        val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = windowInsets.getInsets(WindowInsets.Type.systemBars())
            SystemBarInsets(bars.left, bars.top, bars.right, bars.bottom)
        } else {
            SystemBarInsets(
                windowInsets.systemWindowInsetLeft,
                windowInsets.systemWindowInsetTop,
                windowInsets.systemWindowInsetRight,
                windowInsets.systemWindowInsetBottom,
            )
        }

        scrollView.setPadding(
            0,
            insets.top + dp(74),
            0,
            insets.bottom + dp(24),
        )

        val headerLp = header.layoutParams as FrameLayout.LayoutParams
        headerLp.height = insets.top + dp(78)
        header.layoutParams = headerLp
        header.setTopInset(insets.top)

        val scrimLp = headerScrim.layoutParams as FrameLayout.LayoutParams
        scrimLp.height = insets.top + dp(132)
        headerScrim.layoutParams = scrimLp

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsets.CONSUMED
        } else {
            windowInsets.consumeSystemWindowInsets()
        }
    }

    private fun addSection(section: SettingsSectionSpec) {
        val title = TextView(this).apply {
            text = section.title
            setTextColor(palette.summary)
            setTextSizeDp(13.2f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = true
        }
        content.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = dp(16)
                rightMargin = dp(16)
                bottomMargin = dp(8)
            }
        )

        val card = ModernSettingsCard(this, palette)
        section.items.forEachIndexed { index, item ->
            val showDivider = hasFollowingRow(section.items, index)
            when (item) {
                is SettingsItemSpec.Action -> addAction(card, item, showDivider)
                is SettingsItemSpec.Switch -> addSwitch(card, item, showDivider)
                is SettingsItemSpec.Text -> addTextEditor(card, item, showDivider)
            }
        }
        content.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = dp(22)
            }
        )
    }

    private fun addSwitch(
        card: ModernSettingsCard,
        item: SettingsItemSpec.Switch,
        showDivider: Boolean,
    ) {
        val switchView = ModernSwitchView(this, palette).apply {
            setCheckedImmediately(prefs.getBoolean(item.key, item.defaultValue))
            contentDescription = item.title
            onCheckedChangeListener = { checked ->
                prefs.edit().putBoolean(item.key, checked).apply()
                commitForSection(item.sectionKey)
            }
        }
        val row = ModernSettingsRow(
            this,
            palette,
            item.title,
            item.summary,
            switchView,
            showDivider,
        ).apply {
            setOnClickListener { switchView.performClick() }
        }
        card.addView(row)
    }

    private fun addTextEditor(
        card: ModernSettingsCard,
        item: SettingsItemSpec.Text,
        showDivider: Boolean,
    ) {
        val row = ModernSettingsRow(
            this,
            palette,
            item.title,
            item.summary,
            ModernChevronView(this, palette),
            showDivider,
        ).apply {
            setOnClickListener { showTextEditor(item) }
        }
        card.addView(row)
    }

    private fun addAction(
        card: ModernSettingsCard,
        item: SettingsItemSpec.Action,
        showDivider: Boolean,
    ) {
        if (item.style == ActionStyle.Button) {
            val button = ModernActionButton(this, palette, item.title).apply {
                setOnClickListener { handleAction(item) }
            }
            card.addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(46),
                ).apply {
                    leftMargin = dp(22)
                    rightMargin = dp(22)
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                }
            )
            return
        }

        val row = ModernSettingsRow(
            this,
            palette,
            item.title,
            item.summary,
            ModernChevronView(this, palette),
            showDivider,
        ).apply {
            setOnClickListener { handleAction(item) }
        }
        card.addView(row)
    }

    private fun hasFollowingRow(items: List<SettingsItemSpec>, currentIndex: Int): Boolean {
        for (i in currentIndex + 1 until items.size) {
            val next = items[i]
            return next !is SettingsItemSpec.Action || next.style == ActionStyle.Row
        }
        return false
    }

    private fun showTextEditor(item: SettingsItemSpec.Text) {
        val editText = EditText(this).apply {
            setText(prefs.getString(item.key, ""))
            minLines = 4
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSelectAllOnFocus(false)
        }

        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(
                editText,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }

        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit().putString(item.key, editText.text.toString()).apply()
                commitForSection(item.sectionKey)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun handleAction(item: SettingsItemSpec.Action) {
        when (item.key) {
            "checkHotUpdate" -> showHotUpdateDialog()
            "commit" -> commitSystem()
            "commitSysUI" -> commitSystemUI()
            else -> item.action?.invoke()
        }
    }

    private fun commitForSection(sectionKey: String) {
        when (sectionKey) {
            "system" -> commitSystem()
            "systemui" -> commitSystemUI()
            "miuihome" -> commitMiuiHome()
        }
    }

    private fun loadSectionsFromPrefsXml(): List<SettingsSectionSpec> {
        val sections = mutableListOf<MutableSettingsSectionSpec>()
        var current: MutableSettingsSectionSpec? = null
        val parser = resources.getXml(R.xml.prefs)

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "PreferenceCategory" -> {
                                val section = MutableSettingsSectionSpec(
                                    key = parser.attr("key").orEmpty(),
                                    title = parser.textAttr("title"),
                                )
                                current = section
                                sections.add(section)
                            }

                            "SwitchPreference" -> {
                                val key = parser.attr("key")
                                val section = current
                                if (key != null && section != null) {
                                    section.items.add(
                                        SettingsItemSpec.Switch(
                                            sectionKey = section.key,
                                            key = key,
                                            title = parser.textAttr("title"),
                                            summary = parser.optionalTextAttr("summary"),
                                            defaultValue = parser.getAttributeBooleanValue(
                                                ANDROID_NS,
                                                "defaultValue",
                                                false,
                                            ),
                                        )
                                    )
                                }
                            }

                            "EditTextPreference" -> {
                                val key = parser.attr("key")
                                val section = current
                                if (key != null && section != null) {
                                    section.items.add(
                                        SettingsItemSpec.Text(
                                            sectionKey = section.key,
                                            key = key,
                                            title = parser.textAttr("title"),
                                            summary = parser.optionalTextAttr("summary"),
                                        )
                                    )
                                }
                            }

                            "Preference" -> {
                                val key = parser.attr("key")
                                val section = current
                                if (key != null && section != null) {
                                    section.items.add(
                                        SettingsItemSpec.Action(
                                            sectionKey = section.key,
                                            key = key,
                                            title = parser.textAttr("title"),
                                            summary = parser.optionalTextAttr("summary"),
                                            style = actionStyleFor(key),
                                        )
                                    )
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        if (parser.name == "PreferenceCategory") current = null
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }

        return sections.map { SettingsSectionSpec(it.key, it.title, it.items) }
    }

    private fun XmlResourceParser.attr(name: String): String? =
        getAttributeValue(ANDROID_NS, name)

    private fun XmlResourceParser.optionalTextAttr(name: String): CharSequence? {
        val resId = getAttributeResourceValue(ANDROID_NS, name, 0)
        if (resId != 0) return resources.getText(resId)
        return getAttributeValue(ANDROID_NS, name)
    }

    private fun XmlResourceParser.textAttr(name: String): CharSequence =
        optionalTextAttr(name) ?: ""

    private fun actionStyleFor(key: String): ActionStyle =
        if (key == "commit" || key == "commitSysUI") ActionStyle.Button else ActionStyle.Row

    private fun buildAppsSection(): SettingsSectionSpec? {
        val pm = packageManager
        val items = APP_PACKAGES.mapNotNull { pkg ->
            runCatching {
                val info = try {
                    pm.getApplicationInfo(pkg, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                val label = info.loadLabel(pm)
                SettingsItemSpec.Action(
                    sectionKey = "apps",
                    key = "open:$pkg",
                    title = "打开 $label 设置",
                    summary = pkg,
                    style = ActionStyle.Row,
                    action = {
                        runCatching {
                            val intent = pm.getLaunchIntentForPackage(pkg)
                            if (intent != null) {
                                intent.action = "io.github.a13e300.myinjector.SHOW_SETTINGS"
                                intent.categories.clear()
                                intent.addCategory("io.github.a13e300.myinjector.SHOW_SETTINGS")
                                intent.addCategory(UUID.randomUUID().toString())
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                startActivity(intent)
                            }
                        }.onFailure {
                            logE("failed to open settings for $pkg:", it)
                        }
                    },
                )
            }.onFailure {
                logE("addPackageSettings $pkg", it)
            }.getOrNull()
        }

        if (items.isEmpty()) return null
        return SettingsSectionSpec("apps", "Apps", items)
    }

    private fun commitSystem() {
        val intent = Intent("io.github.a13e300.myinjector.UPDATE_SYSTEM_CONFIG")
        val pendingIntent =
            PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val config = SystemServerConfig.newBuilder()
            .setNoWakePath(prefs.getBoolean("noWakePath", false))
            .setNoMiuiIntent(prefs.getBoolean("noMiuiIntent", false))
            .setClipboardWhitelist(prefs.getBoolean("clipboardWhitelist", false))
            .setFixSync(prefs.getBoolean("fixSync", false))
            .setXSpace(prefs.getBoolean("xSpace", false))
            .setBypassShellDexOptRestriction(
                prefs.getBoolean(
                    "bypassShellDexOptRestriction",
                    false,
                )
            )
            .setNoSwipeToKillLockedProcess(prefs.getBoolean("noSwipeToKillLockedProcess", false))
            .setNoSwipeToKillNoRestrictProcess(
                prefs.getBoolean(
                    "noSwipeToKillNoRestrictProcess",
                    false,
                )
            )
            .addAllClipboardWhitelistPackages(
                Arrays.stream<String?>(
                    prefs.getString("clipboardWhitelistPackages", "")!!.trim { it <= ' ' }
                        .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                )
                    .collect(
                        Collectors.toList()
                    )
            )
            .setForceNewTask(prefs.getBoolean("forceNewTask", false))
            .addAllForceNewTaskRules(
                Arrays.stream<String?>(
                    prefs.getString(
                        "forceNewTaskRules",
                        "",
                    )!!.trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                ).map<NewTaskRule?> { x: String? ->
                    val parts: Array<String?> =
                        x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var ignoreResult = false
                    var useNewDoc = false
                    if (parts.size == 3) {
                        val options: Array<String?> =
                            parts[2]!!.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        for (o in options) {
                            if ("ir" == o) {
                                ignoreResult = true
                            } else if ("nd" == o) {
                                useNewDoc = true
                            }
                        }
                    } else if (parts.size != 2) return@map null
                    var sourcePackage: String = parts[0]!!
                    var targetPackage: String = parts[1]!!
                    var sourceComponent = ""
                    var targetComponent = ""
                    if (sourcePackage.contains("/")) {
                        val l: Array<String?> =
                            sourcePackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        if (l.size == 2) {
                            sourcePackage = l[0]!!
                            sourceComponent = l[1]!!
                            if (sourceComponent.startsWith(".")) sourceComponent = l[0] + l[1]
                        }
                    }
                    if (targetPackage.contains("/")) {
                        val l: Array<String?> =
                            targetPackage.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        if (l.size == 2) {
                            targetPackage = l[0]!!
                            targetComponent = l[1]!!
                            if (targetComponent.startsWith(".")) targetComponent = l[0] + l[1]
                        }
                    }
                    NewTaskRule.newBuilder()
                        .setSourcePackage(sourcePackage)
                        .setTargetPackage(targetPackage)
                        .setSourceComponent(sourceComponent)
                        .setTargetComponent(targetComponent)
                        .setUseNewDocument(useNewDoc)
                        .setIgnoreResult(ignoreResult)
                        .build()
                }.filter { it != null }.collect(Collectors.toList())
            )
            .setOverrideStatusBar(prefs.getBoolean("overrideStatusBar", false))
            .addAllOverrideStatusBarRules(
                Arrays.stream<String?>(
                    prefs.getString("overrideStatusBarRules", "")!!
                        .trim { it <= ' ' }
                        .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                ).map<OverrideStatusBarRule?> { x: String? ->
                    val parts: Array<String?> =
                        x!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (parts.size != 2) return@map null
                    var pkg: String = parts[0]!!
                    var light = false
                    if ("light" == parts[1]) {
                        light = true
                    } else if ("dark" != parts[1]) {
                        return@map null
                    }
                    var component = ""
                    val pkgSplit: Array<String?> =
                        pkg.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (pkgSplit.size == 2) {
                        pkg = pkgSplit[0]!!
                        component = pkgSplit[1]!!
                        if (component.startsWith(".")) component = pkg + component
                    } else if (pkgSplit.size != 1) return@map null
                    OverrideStatusBarRule.newBuilder()
                        .setPackage(pkg)
                        .setComponent(component)
                        .setLight(light)
                        .build()
                }.filter { it != null }.collect(Collectors.toList())
            )
            .setForceNewTaskDebug(prefs.getBoolean("forceNewTaskDebug", false))
            .build()
        intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
        intent.putExtra("EXTRA_CONFIG", config.toByteArray())
        sendBroadcast(intent)
    }

    private fun commitSystemUI() {
        val intent = Intent("io.github.a13e300.myinjector.UPDATE_SYSTEMUI_CONFIG")
        val pendingIntent =
            PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val config = SystemUIConfig.newBuilder()
            .setAlwaysExpandNotification(prefs.getBoolean("alwaysExpandNotification", false))
            .setNoDndNotification(prefs.getBoolean("noDndNotification", false))
            .setShowNotificationDetail(prefs.getBoolean("showNotificationDetail", false))
            .setFixWhiteSplash(prefs.getBoolean("fixWhiteSplash", false))
            .build()
        intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
        intent.putExtra("EXTRA_CONFIG", config.toByteArray())
        sendBroadcast(intent)
    }

    private fun commitMiuiHome() {
        val intent = Intent("io.github.a13e300.myinjector.UPDATE_MIUI_HOME_CONFIG")
        val pendingIntent =
            PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
        val config = MiuiHomeConfig.newBuilder()
            .setOpenAospSettings(prefs.getBoolean("miuiHomeOpenAospSettings", true))
            .setDragKill(prefs.getBoolean("miuiHomeDragKill", true))
            .setDisablePreLaunch(prefs.getBoolean("miuiHomeDisablePreLaunch", true))
            .build()
        intent.putExtra("EXTRA_CREDENTIAL", pendingIntent)
        intent.putExtra("EXTRA_CONFIG", config.toByteArray())
        sendBroadcast(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun showHotUpdateDialog() {
        val rootView =
            LayoutInflater.from(this).inflate(R.layout.hot_update_dialog, null, false)

        val tv = rootView.findViewById<TextView>(R.id.result_text)

        fun command(name: String, args: Bundle.() -> Unit = {}, cb: (Int, Bundle?) -> Unit) {
            val intent = Intent("io.github.a13e300.myinjector.SYSTEM_SERVER_ENTRY")
            intent.`package` = "android"
            val pendingIntent =
                PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
            val receiver = object : ResultReceiver() {
                override fun onReceive(code: Int, data: Bundle?) {
                    cb(code, data)
                }
            }
            val b = Bundle().apply {
                putParcelable("EXTRA_CREDENTIAL", pendingIntent)
                putString("EXTRA_ACTION", name)
                putBinder("EXTRA_RECEIVER", receiver)
                args()
            }
            intent.putExtras(b)
            sendBroadcast(intent)
        }

        fun check() {
            tv.text = "检查中……"
            command("needUpdate") { code, _ ->
                tv.post {
                    tv.text = if (code == 1) "可更新" else "无需更新"
                }
            }
        }

        fun update(force: Boolean) {
            command("reload", { putBoolean("force", force) }) { code, _ ->
                tv.post {
                    tv.text = if (code == 1) "重新加载成功" else "重新加载失败"
                }
            }
        }

        rootView.findViewById<Button>(R.id.update_btn).setOnClickListener {
            update(false)
        }

        rootView.findViewById<Button>(R.id.update_force_btn).setOnClickListener {
            update(true)
        }

        rootView.findViewById<Button>(R.id.check_btn).setOnClickListener {
            check()
        }

        rootView.findViewById<Button>(R.id.gc_btn).setOnClickListener {
            command("gc") { code, _ ->
                tv.post {
                    tv.text = if (code == 0) "GC 成功" else "GC 失败"
                }
            }
        }

        rootView.findViewById<Button>(R.id.old_hook_btn).setOnClickListener {
            command("reportOldHook") { code, data ->
                val d = data?.getString("hooks") ?: ""
                tv.post {
                    tv.text = "旧模块：" + d.ifEmpty { "无" }
                }
            }
        }

        check()

        AlertDialog.Builder(this)
            .setTitle("热更新")
            .setView(rootView)
            .show()
    }

    private data class SettingsSectionSpec(
        val key: String,
        val title: CharSequence,
        val items: List<SettingsItemSpec>,
    )

    private data class MutableSettingsSectionSpec(
        val key: String,
        val title: CharSequence,
        val items: MutableList<SettingsItemSpec> = mutableListOf(),
    )

    private sealed class SettingsItemSpec {
        abstract val sectionKey: String
        abstract val key: String
        abstract val title: CharSequence
        abstract val summary: CharSequence?

        data class Switch(
            override val sectionKey: String,
            override val key: String,
            override val title: CharSequence,
            override val summary: CharSequence?,
            val defaultValue: Boolean,
        ) : SettingsItemSpec()

        data class Text(
            override val sectionKey: String,
            override val key: String,
            override val title: CharSequence,
            override val summary: CharSequence?,
        ) : SettingsItemSpec()

        data class Action(
            override val sectionKey: String,
            override val key: String,
            override val title: CharSequence,
            override val summary: CharSequence?,
            val style: ActionStyle,
            val action: (() -> Unit)? = null,
        ) : SettingsItemSpec()
    }

    private enum class ActionStyle {
        Row,
        Button,
    }

    private data class SystemBarInsets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )

    companion object {
        private const val PREFS_NAME = "system_server"
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

        private val APP_PACKAGES = listOf(
            "com.xingin.xhs",
            "com.kiwibrowser.browser",
            "com.android.chrome",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.messenger.beta",
            "org.telegram.plus",
            "com.exteragram.messenger",
            "com.radolyn.ayugram",
            "uz.unnarsx.cherrygram",
            "xyz.nextalone.nagram",
            "nu.gpu.nagram",
            "com.xtaolabs.pagergram",
            "fork.risin42.nagramx",
        )
    }
}
