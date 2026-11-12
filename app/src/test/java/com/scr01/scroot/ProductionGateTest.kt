package com.scr01.scroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionGateTest {

    @Test
    fun bootScopedStatusFollowsTheCurrentBootId() {
        assertNull(
            AutoRootPreferences.bootScopedValue(
                "boot-a",
                "boot-b",
                AutoRootPreferences.STATUS_SUCCESS
            )
        )
        assertEquals(
            AutoRootPreferences.STATUS_REBOOT_REQUIRED,
            AutoRootPreferences.bootScopedValue(
                "boot-a",
                "boot-a",
                AutoRootPreferences.STATUS_REBOOT_REQUIRED
            )
        )
        assertNull(AutoRootPreferences.bootScopedValue(null, "boot-a", "running"))
        assertNull(AutoRootPreferences.bootScopedValue("boot-a", null, "running"))
    }

    @Test
    fun duplicateStartUsesTheSameGateAsTheManualButton() {
        assertTrue(
            pipelineIsActive(
                rootFlowRunning = false,
                autoServiceActive = true,
                manualGuardActive = false
            )
        )
        assertFalse(
            manualStartAllowed(
                automaticActive = true,
                manualActive = false,
                flowRunning = false
            )
        )
        assertFalse(
            manualStartAllowed(
                automaticActive = false,
                manualActive = true,
                flowRunning = false
            )
        )
        assertFalse(
            manualStartAllowed(
                automaticActive = false,
                manualActive = false,
                flowRunning = true
            )
        )
        assertTrue(
            manualStartAllowed(
                automaticActive = false,
                manualActive = false,
                flowRunning = false
            )
        )
        assertFalse(pipelineIsActive(false, false, false))
    }

    @Test
    fun liveModuleRepairDoesNotRequestAnotherExploit() {
        assertEquals(0, manualExploitAttemptLimit(moduleLive = true))
        assertEquals(1, manualExploitAttemptLimit(moduleLive = false))
        assertEquals(
            ManualFlowOutcome.UI_REPAIR,
            manualFlowOutcome(
                rooted = true,
                moduleLoaded = true,
                managerCrowned = true,
                userspaceReady = true,
                systemUiIntegrated = false,
                exploitAttempted = false
            )
        )
        assertEquals(
            ManualFlowOutcome.EXPLOIT_REBOOT,
            manualFlowOutcome(
                rooted = false,
                moduleLoaded = false,
                managerCrowned = false,
                userspaceReady = false,
                systemUiIntegrated = false,
                exploitAttempted = true
            )
        )
        assertEquals(
            ManualFlowOutcome.PREFLIGHT_BLOCKED,
            manualFlowOutcome(
                rooted = false,
                moduleLoaded = false,
                managerCrowned = false,
                userspaceReady = false,
                systemUiIntegrated = false,
                exploitAttempted = false
            )
        )
        assertEquals(
            ManualFlowOutcome.COMPLETE,
            manualFlowOutcome(
                rooted = true,
                moduleLoaded = true,
                managerCrowned = true,
                userspaceReady = true,
                systemUiIntegrated = true,
                exploitAttempted = true
            )
        )
    }

    @Test
    fun uiRepairStaysDistinctFromAMapperWarning() {
        val warning = RootFlow.postRootHealthWarning(
            listOf("SYSTEMUI_PID=5363", "MAPPER=missing", "WIFI_ON=1", "HEALTH_END")
        )
        assertEquals(
            "SystemUI cannot see the graphics mapper. Root was not changed, and vendor was not unmounted.",
            warning
        )
        assertNull(
            RootFlow.postRootHealthWarning(
                listOf("SYSTEMUI_PID=5363", "MAPPER=present", "WIFI_ON=1", "HEALTH_END")
            )
        )
        assertEquals(
            "SystemUI health is unknown. Root was not changed.",
            RootFlow.postRootHealthWarning(listOf("HEALTH_TIMEOUT"))
        )
        assertEquals(
            "SystemUI health is unknown. Root was not changed.",
            RootFlow.postRootHealthWarning(listOf("MAPPER=unknown", "HEALTH_END"))
        )
        assertTrue(UI_REPAIR_GUIDANCE.contains("Root stays active"))
        assertTrue(UI_REPAIR_GUIDANCE.contains("no rollback control"))
        assertFalse(UI_REPAIR_GUIDANCE.contains("unmount vendor"))
    }

    @Test
    fun testSignerAcceptsConnectedRecentsWhenTheOfficialProviderIsAbsent() {
        assertFalse(
            RootFlow.overviewReadyForCaller(
                officialCaller = true,
                providerReady = false,
                functionalRecents = true
            )
        )
        assertTrue(
            RootFlow.overviewReadyForCaller(
                officialCaller = false,
                providerReady = false,
                functionalRecents = true
            )
        )
        assertFalse(
            RootFlow.overviewReadyForCaller(
                officialCaller = false,
                providerReady = false,
                functionalRecents = false
            )
        )
        assertTrue(
            RootFlow.functionalRecentsFromFlags(
                quickStepResolved = true,
                quickStepConnected = true,
                layoutRecent = false,
                recentsComponent = true
            )
        )
        assertFalse(
            RootFlow.functionalRecentsFromFlags(
                quickStepResolved = true,
                quickStepConnected = false,
                layoutRecent = true,
                recentsComponent = true
            )
        )
        assertEquals(
            true,
            RootFlow.diagnosticFlag(
                listOf("QUICKSTEP_RESOLVED=0", "QUICKSTEP_RESOLVED=1"),
                "QUICKSTEP_RESOLVED"
            )
        )
        assertNull(RootFlow.diagnosticFlag(listOf("OTHER=1"), "QUICKSTEP_RESOLVED"))
    }

    @Test
    fun uninstalledOptionalAppIsNotLaunchable() {
        assertFalse(
            AppDrawer.launcherEligible(
                "com.wangzan101.mika",
                enabled = true,
                launcherActivity = null
            )
        )
        assertFalse(
            AppDrawer.launcherEligible(
                "com.android.deskclock",
                enabled = false,
                launcherActivity = ".DeskClock"
            )
        )
        assertTrue(
            AppDrawer.launcherEligible(
                "com.android.deskclock",
                enabled = true,
                launcherActivity = ".DeskClock"
            )
        )
        val visible = AppDrawer.sortForDrawer(
            listOf(
                LaunchableApp("com.scr01.scroot", ".MainActivity", "SCRoot"),
                LaunchableApp("com.android.deskclock", ".DeskClock", "Clock")
            )
        )
        assertEquals(listOf("com.android.deskclock"), visible.map { it.packageName })
    }

    @Test
    fun diagnosticsDropSecretLinesAndStayBounded() {
        val lines = redactedDiagnosticLines(
            List(50) { "line-$it" } + listOf("password=hidden", "token abc"),
            limit = 5
        )
        assertEquals(5, lines.size)
        assertFalse(lines.any { it.contains("password") || it.contains("token") })
        assertEquals("line-49", lines.last())
    }

    @Test
    fun reclaimMissIsNotPostUafAndStillLatchesTheBootStatus() {
        assertFalse(RootFlow.NativeOutcome.RECLAIM_MISS.postUaf)
        assertTrue(RootFlow.NativeOutcome.UAF_DIRTY.postUaf)
        assertFalse(RootFlow.NativeOutcome.SAFE_ABORT.postUaf)
        assertEquals(
            AutoRootPreferences.STATUS_REBOOT_REQUIRED,
            AutoRootPreferences.interruptionStatus(
                exploitRecorded = true,
                moduleLoaded = false
            )
        )
        assertEquals(
            AutoRootPreferences.STATUS_SAFE_FAILURE,
            AutoRootPreferences.interruptionStatus(
                exploitRecorded = false,
                moduleLoaded = true
            )
        )
    }
}
