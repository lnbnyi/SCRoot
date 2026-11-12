package com.scr01.scroot

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * SCRoot's own app list. It reads launchable activities already visible to
 * this app. It does not bind-mount MHSHome, change the default launcher, or
 * enable the SystemUI overview bridge.
 */
class AppListActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density + 0.5f).toInt()
        val background = 0xFFF6F6F6.toInt()
        val surface = 0xFFFCFCFC.toInt()
        val textPrimary = 0xFF17191C.toInt()
        val textSecondary = 0xFF656A72.toInt()
        val regular = Typeface.create("sans-serif", Typeface.NORMAL)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        content.addView(label("Apps", 24f, textPrimary, medium))
        content.addView(label(
            "Installed apps opened from SCRoot. The stock Home sidebar is not changed.",
            13f,
            textSecondary,
            regular
        ).apply {
            setPadding(0, dp(6), 0, dp(16))
        })

        if (overviewAppsResolves()) {
            content.addView(row(
                title = "Installed Apps screen",
                detail = "Opens the already installed SCR Apps activity if Android allows it.",
                icon = null,
                dp = ::dp,
                surface = surface,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                regular = regular,
                medium = medium
            ) {
                openIntent(AppDrawer.overviewAppsIntent(), privilegedAppsScreen = true)
            })
            content.addView(spacer(dp(10)))
        }

        val apps = loadLaunchableApps()
        if (apps.isEmpty()) {
            content.addView(label(
                "No other launchable apps are visible to SCRoot.",
                14f,
                textSecondary,
                regular
            ))
        } else {
            apps.forEach { app ->
                content.addView(row(
                    title = app.label,
                    detail = app.packageName,
                    icon = appIcon(app),
                    dp = ::dp,
                    surface = surface,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    regular = regular,
                    medium = medium
                ) {
                    openIntent(AppDrawer.launchIntent(app), privilegedAppsScreen = false)
                })
                content.addView(spacer(dp(8)))
            }
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(background)
            isFillViewport = true
            addView(content)
        })
        window.statusBarColor = surface
        window.navigationBarColor = background
    }

    private fun overviewAppsResolves(): Boolean = try {
        packageManager.getActivityInfo(AppDrawer.overviewAppsComponent(), 0).exported
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun loadLaunchableApps(): List<LaunchableApp> {
        val resolved = try {
            packageManager.queryIntentActivities(
                AppDrawer.launcherQueryIntent(),
                PackageManager.MATCH_DEFAULT_ONLY
            )
        } catch (_: RuntimeException) {
            emptyList()
        }
        val apps = resolved.mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val packageName = activity.packageName ?: return@mapNotNull null
            val activityName = activity.name ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                .ifEmpty { packageName }
            LaunchableApp(packageName, activityName, label)
        }
        return AppDrawer.sortForDrawer(apps)
    }

    private fun appIcon(app: LaunchableApp) = try {
        packageManager.getActivityInfo(
            android.content.ComponentName(app.packageName, app.activityName),
            0
        ).loadIcon(packageManager)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun openIntent(intent: Intent, privilegedAppsScreen: Boolean) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(
                if (privilegedAppsScreen) {
                    "The installed Apps screen is not available. The list below is unchanged."
                } else {
                    "That app could not be opened."
                }
            )
        } catch (_: SecurityException) {
            toast(
                if (privilegedAppsScreen) {
                    "Android blocked the installed Apps screen. The SystemUI bridge was not enabled."
                } else {
                    "Android blocked that app launch."
                }
            )
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun label(
        text: String,
        size: Float,
        color: Int,
        typeface: Typeface
    ): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        this.typeface = typeface
        includeFontPadding = false
    }

    private fun spacer(height: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            height
        )
    }

    private fun row(
        title: String,
        detail: String,
        icon: android.graphics.drawable.Drawable?,
        dp: (Int) -> Int,
        surface: Int,
        textPrimary: Int,
        textSecondary: Int,
        regular: Typeface,
        medium: Typeface,
        onClick: () -> Unit
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadius = dp(18).toFloat()
        }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        isClickable = true
        isFocusable = true
        contentDescription = "$title, $detail"
        setOnClickListener { onClick() }
        if (icon != null) {
            addView(ImageView(this@AppListActivity).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).also {
                    it.marginEnd = dp(12)
                }
            })
        }
        addView(LinearLayout(this@AppListActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(label(title, 16f, textPrimary, medium))
            addView(label(detail, 12f, textSecondary, regular).apply {
                setPadding(0, dp(3), 0, 0)
            })
        })
    }
}
