package com.scr01.scroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RootMenuTest {
    private val installed = listOf(
        ScannedApp("com.android.deskclock", "时钟"),
        ScannedApp("com.box.app", "Box"),
        ScannedApp("com.follow.clash", "FlClash"),
        ScannedApp("com.github.surfing", "Surfing"),
        ScannedApp("com.scr01.scroot", "SCRoot"),
        ScannedApp("com.rifsxd.ksunext", "KSU Next"),
        ScannedApp("com.microsoft.launcher", "微软"),
        ScannedApp("com.example.menukey", "MenuKey")
    )

    @Test
    fun firstOpenSeedsKnownAppsAndARealMenuKeyMatch() {
        val menu = RootMenu.withDefaults(null, installed)
        val names = menu.categories.map { it.name }
        assertEquals(listOf("时钟", "按键与手势", "透明代理", "Root工具", "启动器"), names)
        assertEquals(listOf("com.android.deskclock"), menu.categories[0].entries.map { it.packageName })
        assertEquals(listOf("com.example.menukey"), menu.categories[1].entries.map { it.packageName })
        assertEquals(
            listOf("com.box.app", "com.follow.clash", "com.github.surfing"),
            menu.categories[2].entries.map { it.packageName }
        )
        assertFalse(menu.categories.any { category ->
            category.entries.any { it.packageName == "com.wangzan101.mika" }
        })
    }

    @Test
    fun savedMenuIsNotReplacedOnLaterOpens() {
        val saved = RootMenu.addCategory(
            RootMenu.withDefaults(null, installed),
            "custom",
            "我的"
        )
        val again = RootMenu.withDefaults(saved, installed)
        assertEquals(saved, again)
        assertEquals(saved, RootMenu.decode(RootMenu.encode(saved)))
    }

    @Test
    fun reorderRemoveAndEmptyCategoryStayOutOfTheSidebar() {
        val saved = RootMenu.withDefaults(null, installed)
        val moved = RootMenu.moveCategory(saved, 2, -2)
        assertEquals("透明代理", moved.categories.first().name)
        val proxy = moved.categories.first { it.id == "proxy" }
        val removed = RootMenu.removeEntry(moved, proxy.id, "com.box.app")
        assertFalse(removed.categories.first { it.id == "proxy" }.entries.any { it.packageName == "com.box.app" })
        val visible = RootMenu.visible(removed, installed.map { it.packageName }.toSet() - "com.box.app")
        assertTrue(visible.none { it.entries.any { entry -> entry.packageName == "com.box.app" } })
        val emptied = removed.copy(categories = removed.categories.map {
            if (it.id == "keys") it.copy(entries = emptyList()) else it
        })
        assertTrue(RootMenu.visible(emptied, installed.map { it.packageName }.toSet()).none { it.name == "按键与手势" })
    }

    @Test
    fun missingPackageStaysSavedAndReturnsWhenLaunchableAgain() {
        val saved = RootMenu.withDefaults(null, installed)
        val withoutMikaChoice = RootMenu.addEntry(
            saved,
            "proxy",
            ScannedApp("com.example.absent", "不存在")
        )
        val hidden = RootMenu.visible(withoutMikaChoice, installed.map { it.packageName }.toSet())
        assertTrue(hidden.none { category -> category.entries.any { it.packageName == "com.example.absent" } })
        assertTrue(withoutMikaChoice.categories.any { category ->
            category.entries.any { it.packageName == "com.example.absent" }
        })
        val back = RootMenu.visible(
            withoutMikaChoice,
            installed.map { it.packageName }.toSet() + "com.example.absent"
        )
        assertTrue(back.any { category -> category.entries.any { it.packageName == "com.example.absent" } })
    }

    @Test
    fun noRootHashStaysUnconfirmedAndOldBridgeAsksForOneRepair() {
        assertEquals(MenuSync.UNCONFIRMED, RootMenu.syncState(null, "abc"))
        assertEquals(MenuSync.BRIDGE_CURRENT, RootMenu.syncState("abc", "abc"))
        assertEquals(MenuSync.BRIDGE_OLD, RootMenu.syncState("old", "abc"))
        assertTrue(RootMenu.syncMessage(MenuSync.UNCONFIRMED).contains("不会显示成已经生效"))
        assertTrue(RootMenu.syncMessage(MenuSync.BRIDGE_CURRENT).contains("不会重新 root"))
    }

    @Test
    fun menuPrefsAreSeparateFromAutomaticRootPrefs() {
        assertEquals("root_menu", RootMenu.PREFS_NAME)
        assertFalse(RootMenu.PREFS_NAME == "auto_root")
    }

    @Test
    fun callerIsOnlyHomeOrThisApp() {
        assertTrue(callerAllowed(arrayOf("com.samsung.android.mhshome"), "com.scr01.scroot"))
        assertTrue(callerAllowed(arrayOf("com.scr01.scroot"), "com.scr01.scroot"))
        assertFalse(callerAllowed(arrayOf("com.example.other"), "com.scr01.scroot"))
        assertFalse(callerAllowed(null, "com.scr01.scroot"))
    }

    @Test
    fun labelsCannotCarryControlSeparators() {
        assertEquals("Box", RootMenu.cleanLabel("Box\n\t\u001f", "按钮"))
        assertNull(RootMenu.decode("nope"))
        assertFalse(RootMenu.acceptsPackage("not a package"))
    }
}
