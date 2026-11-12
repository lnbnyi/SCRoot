package com.scr01.scroot

/**
 * Truthful setup outcomes. These do not start exploits, change kernel umount,
 * or reboot the device.
 */
internal enum class SetupDisposition {
    PRE_EXPLOIT_BLOCKED,
    RECLAIM_MISS_LATCHED,
    POST_UAF_REBOOT_REQUIRED,
    EXPLOIT_REBOOT_REQUIRED,
    CORE_READY_UI_UNAVAILABLE,
    COMPLETE
}

internal fun setupDisposition(
    rooted: Boolean,
    exploitAttempted: Boolean,
    moduleLoaded: Boolean,
    userspaceReady: Boolean,
    managerReady: Boolean,
    uiIntegrated: Boolean,
    nativeOutcome: RootFlow.NativeOutcome?
): SetupDisposition {
    val coreReady = rooted && moduleLoaded && userspaceReady && managerReady
    if (coreReady && uiIntegrated) return SetupDisposition.COMPLETE
    if (coreReady) return SetupDisposition.CORE_READY_UI_UNAVAILABLE
    if (!exploitAttempted || !rooted && nativeOutcome == null && !exploitAttempted) {
        return SetupDisposition.PRE_EXPLOIT_BLOCKED
    }
    if (!exploitAttempted) return SetupDisposition.PRE_EXPLOIT_BLOCKED
    return when (nativeOutcome) {
        RootFlow.NativeOutcome.RECLAIM_MISS -> SetupDisposition.RECLAIM_MISS_LATCHED
        RootFlow.NativeOutcome.UAF_DIRTY,
        RootFlow.NativeOutcome.PGD_AMBIGUOUS,
        RootFlow.NativeOutcome.WRITE_PARTIAL,
        RootFlow.NativeOutcome.PATCH_DIRTY -> SetupDisposition.POST_UAF_REBOOT_REQUIRED
        RootFlow.NativeOutcome.SAFE_ABORT,
        RootFlow.NativeOutcome.ROOT_VERIFIED,
        null -> if (rooted) {
            SetupDisposition.EXPLOIT_REBOOT_REQUIRED
        } else if (nativeOutcome == RootFlow.NativeOutcome.SAFE_ABORT) {
            SetupDisposition.PRE_EXPLOIT_BLOCKED
        } else {
            SetupDisposition.EXPLOIT_REBOOT_REQUIRED
        }
    }
}

internal fun allowsAnotherExploit(disposition: SetupDisposition): Boolean = when (disposition) {
    SetupDisposition.PRE_EXPLOIT_BLOCKED -> false
    SetupDisposition.RECLAIM_MISS_LATCHED,
    SetupDisposition.POST_UAF_REBOOT_REQUIRED,
    SetupDisposition.EXPLOIT_REBOOT_REQUIRED,
    SetupDisposition.CORE_READY_UI_UNAVAILABLE,
    SetupDisposition.COMPLETE -> false
}

internal fun manualStartAllowed(
    automaticActive: Boolean,
    manualActive: Boolean,
    flowRunning: Boolean
): Boolean = !automaticActive && !manualActive && !flowRunning

internal fun attemptVisibleForBoot(
    storedBootId: String?,
    currentBootId: String?,
    status: String?
): String? = if (
    storedBootId != null &&
    currentBootId != null &&
    storedBootId == currentBootId
) {
    status
} else {
    null
}

internal data class OptionalApp(
    val label: String,
    val packageName: String
)

internal val sidebarOptionalApps: List<OptionalApp> = listOf(
    OptionalApp("Microsoft Launcher", "com.microsoft.launcher"),
    OptionalApp("FlClash", "com.follow.clash"),
    OptionalApp("Mika", "com.wangzan101.mika"),
    OptionalApp("SurfingTile", "com.github.surfing"),
    OptionalApp("Flip Clock", "com.zwh.flip.clock"),
    OptionalApp("Alynx Clock", "one.alynx.flipclock"),
    OptionalApp("Clock", "com.android.deskclock")
)

internal fun visibleSidebarLabels(launchablePackages: Set<String>): List<String> {
    val root = mutableListOf<String>()
    if ("com.scr01.scroot" in launchablePackages) root += "SCRoot"
    if ("com.rifsxd.ksunext" in launchablePackages) root += "KSU Next"
    sidebarOptionalApps.forEach { app ->
        if (app.packageName in launchablePackages) root += app.label
    }
    return root
}

internal fun launcherEligible(
    packageName: String,
    enabled: Boolean,
    launcherActivity: String?
): Boolean = packageName.isNotBlank() &&
    packageName != AppDrawer.SCROOT_PACKAGE &&
    enabled &&
    !launcherActivity.isNullOrBlank()
