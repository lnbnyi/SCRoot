package com.scr01.scroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupStatusTest {

    @Test
    fun missingOptionalAppIsHidden() {
        val visible = visibleSidebarLabels(
            setOf("com.scr01.scroot", "com.android.deskclock")
        )
        assertEquals(listOf("SCRoot", "Clock"), visible)
        assertFalse(visible.contains("Mika"))
    }

    @Test
    fun disabledOrUnlaunchablePackageIsNotEligible() {
        assertFalse(launcherEligible("com.wangzan101.mika", enabled = true, launcherActivity = null))
        assertFalse(launcherEligible("com.android.deskclock", enabled = false, launcherActivity = ".DeskClock"))
        assertTrue(launcherEligible("com.android.deskclock", enabled = true, launcherActivity = ".DeskClock"))
    }

    @Test
    fun coreSuccessWithUiFailureDoesNotAllowAnotherExploit() {
        val disposition = setupDisposition(
            rooted = true,
            exploitAttempted = true,
            moduleLoaded = true,
            userspaceReady = true,
            managerReady = true,
            uiIntegrated = false,
            nativeOutcome = RootFlow.NativeOutcome.ROOT_VERIFIED
        )
        assertEquals(SetupDisposition.CORE_READY_UI_UNAVAILABLE, disposition)
        assertFalse(allowsAnotherExploit(disposition))
        assertEquals(0, manualExploitAttemptLimit(moduleLive = true))
    }

    @Test
    fun reclaimMissLatchesUntilReboot() {
        val disposition = setupDisposition(
            rooted = false,
            exploitAttempted = true,
            moduleLoaded = false,
            userspaceReady = false,
            managerReady = false,
            uiIntegrated = false,
            nativeOutcome = RootFlow.NativeOutcome.RECLAIM_MISS
        )
        assertEquals(SetupDisposition.RECLAIM_MISS_LATCHED, disposition)
        assertFalse(allowsAnotherExploit(disposition))
        assertFalse(RootFlow.NativeOutcome.RECLAIM_MISS.postUaf)
        assertEquals(
            null,
            attemptVisibleForBoot("boot-a", "boot-b", AutoRootPreferences.STATUS_SUCCESS)
        )
        assertEquals(
            AutoRootPreferences.STATUS_REBOOT_REQUIRED,
            attemptVisibleForBoot(
                "boot-a",
                "boot-a",
                AutoRootPreferences.STATUS_REBOOT_REQUIRED
            )
        )
    }

    @Test
    fun simultaneousAutomaticAttemptBlocksManualStart() {
        assertFalse(
            manualStartAllowed(
                automaticActive = true,
                manualActive = false,
                flowRunning = false
            )
        )
        assertTrue(
            manualStartAllowed(
                automaticActive = false,
                manualActive = false,
                flowRunning = false
            )
        )
    }

    @Test
    fun preExploitAbortIsNotPostUaf() {
        val disposition = setupDisposition(
            rooted = false,
            exploitAttempted = true,
            moduleLoaded = false,
            userspaceReady = false,
            managerReady = false,
            uiIntegrated = false,
            nativeOutcome = RootFlow.NativeOutcome.SAFE_ABORT
        )
        assertEquals(SetupDisposition.PRE_EXPLOIT_BLOCKED, disposition)
        assertTrue(RootFlow.NativeOutcome.UAF_DIRTY.postUaf)
    }
}
