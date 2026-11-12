package com.scr01.scroot

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDrawerTest {

    @Test
    fun drawerSortsPreferredAppsFirstAndDropsThisApp() {
        val sorted = AppDrawer.sortForDrawer(
            listOf(
                LaunchableApp("com.scr01.scroot", ".MainActivity", "SCRoot"),
                LaunchableApp("com.example.notes", ".Main", "Notes"),
                LaunchableApp("com.android.deskclock", ".DeskClock", "Clock"),
                LaunchableApp("com.microsoft.launcher", ".Launcher", "Microsoft Launcher"),
                LaunchableApp("com.follow.clash", ".MainActivity", "FlClash"),
                LaunchableApp("com.wangzan101.mika", ".MainActivity", "Mika"),
                LaunchableApp("com.github.surfing", ".ui.LauncherActivity", "SurfingTile"),
                LaunchableApp("com.zwh.flip.clock", ".MainActivity", "Flip Clock"),
                LaunchableApp("one.alynx.flipclock", ".MainActivity", "Alynx Clock"),
                LaunchableApp("com.example.notes", ".Main", "Notes"),
                LaunchableApp("com.example.blank", "", "Blank")
            )
        )

        assertEquals(
            listOf(
                "com.microsoft.launcher",
                "com.follow.clash",
                "com.wangzan101.mika",
                "com.github.surfing",
                "com.zwh.flip.clock",
                "one.alynx.flipclock",
                "com.android.deskclock",
                "com.example.notes"
            ),
            sorted.map { it.packageName }
        )
    }

    @Test
    fun missingPreferredAppsStayAbsent() {
        val sorted = AppDrawer.sortForDrawer(
            listOf(LaunchableApp("com.example.notes", ".Main", "Notes"))
        )
        assertEquals(listOf("com.example.notes"), sorted.map { it.packageName })
    }

    @Test
    fun launchesStayExplicitAndDoNotRequestHome() {
        val app = LaunchableApp(
            "com.github.surfing",
            ".ui.LauncherActivity",
            "SurfingTile"
        )
        val launch = AppDrawer.appLaunch(app)
        val overview = AppDrawer.overviewLaunch()
        val homeRole = overview.copy(categories = setOf(Intent.CATEGORY_HOME))

        assertTrue(AppDrawer.isSafeExplicitLaunch(launch))
        assertTrue(AppDrawer.isSafeExplicitLaunch(overview))
        assertFalse(AppDrawer.isSafeExplicitLaunch(homeRole))
        assertFalse(
            AppDrawer.isSafeExplicitLaunch(
                launch.copy(packageName = "", categories = setOf(Intent.CATEGORY_LAUNCHER))
            )
        )
        assertEquals("com.github.surfing", launch.packageName)
        assertEquals(".ui.LauncherActivity", launch.className)
        assertEquals(setOf(Intent.CATEGORY_LAUNCHER), launch.categories)
        assertEquals(AppDrawer.OVERVIEW_PACKAGE, overview.packageName)
        assertEquals(AppDrawer.OVERVIEW_APPS_ACTIVITY, overview.className)
        assertFalse(Intent.CATEGORY_HOME in launch.categories)
        assertFalse(Intent.CATEGORY_HOME in overview.categories)
        assertEquals(Intent.ACTION_MAIN, launch.action)
        assertEquals(Intent.ACTION_MAIN, overview.action)
    }
}
