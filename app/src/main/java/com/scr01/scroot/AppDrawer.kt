package com.scr01.scroot

import android.content.ComponentName
import android.content.Intent

/**
 * In-app app list for SCRoot.
 *
 * This is not the stock MHSHome sidebar. Those Root and Apps entries live in
 * the on-device `scr01_scroot_menu` bind mount and are unchanged here. Launch
 * intents name an explicit component, never the HOME role, and this type does
 * not enable `scr01_overview_bridge` or kernel umount.
 */
internal data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val label: String
)

internal data class ExplicitLaunch(
    val action: String,
    val packageName: String,
    val className: String,
    val categories: Set<String>
)

internal object AppDrawer {
    const val OVERVIEW_PACKAGE = "com.sec.android.app.launcher"
    const val OVERVIEW_APPS_ACTIVITY = "com.scr01.overview.AllAppsActivity"
    const val SCROOT_PACKAGE = "com.scr01.scroot"

    val preferredPackages: List<String> = listOf(
        "com.microsoft.launcher",
        "com.follow.clash",
        "com.wangzan101.mika",
        "com.github.surfing",
        "com.zwh.flip.clock",
        "one.alynx.flipclock",
        "com.android.deskclock"
    )

    fun overviewAppsComponent(): ComponentName =
        ComponentName(OVERVIEW_PACKAGE, OVERVIEW_APPS_ACTIVITY)

    fun overviewLaunch(): ExplicitLaunch = ExplicitLaunch(
        action = Intent.ACTION_MAIN,
        packageName = OVERVIEW_PACKAGE,
        className = OVERVIEW_APPS_ACTIVITY,
        categories = emptySet()
    )

    fun appLaunch(app: LaunchableApp): ExplicitLaunch = ExplicitLaunch(
        action = Intent.ACTION_MAIN,
        packageName = app.packageName,
        className = app.activityName,
        categories = setOf(Intent.CATEGORY_LAUNCHER)
    )

    fun overviewAppsIntent(): Intent = intentFor(overviewLaunch())

    fun launchIntent(app: LaunchableApp): Intent = intentFor(appLaunch(app))

    fun launcherQueryIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    fun includeInDrawer(packageName: String): Boolean =
        launcherEligible(packageName, enabled = true, launcherActivity = packageName)

    fun sortForDrawer(apps: List<LaunchableApp>): List<LaunchableApp> {
        val rank = preferredPackages.withIndex().associate { it.value to it.index }
        return apps
            .filter {
                launcherEligible(it.packageName, enabled = true, launcherActivity = it.activityName)
            }
            .distinctBy { it.packageName to it.activityName }
            .sortedWith(
                compareBy<LaunchableApp>(
                    { rank[it.packageName] ?: Int.MAX_VALUE },
                    { it.label.lowercase() },
                    { it.packageName },
                    { it.activityName }
                )
            )
    }

    fun isSafeExplicitLaunch(launch: ExplicitLaunch): Boolean {
        if (launch.action != Intent.ACTION_MAIN) return false
        if (launch.packageName.isBlank() || launch.className.isBlank()) return false
        if (Intent.CATEGORY_HOME in launch.categories) return false
        if (Intent.CATEGORY_DEFAULT in launch.categories) return false
        return launch.categories.isEmpty() ||
            launch.categories == setOf(Intent.CATEGORY_LAUNCHER)
    }

    internal fun launcherEligible(
        packageName: String,
        enabled: Boolean,
        launcherActivity: String?
    ): Boolean = packageName.isNotBlank() &&
        packageName != SCROOT_PACKAGE &&
        enabled &&
        !launcherActivity.isNullOrBlank()

    private fun intentFor(launch: ExplicitLaunch): Intent {
        check(isSafeExplicitLaunch(launch))
        return Intent(launch.action).apply {
            component = ComponentName(launch.packageName, launch.className)
            launch.categories.forEach(::addCategory)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
