package com.scr01.scroot

import android.content.Intent
import android.content.pm.PackageManager

internal object InstalledApps {
    fun scan(packageManager: PackageManager): List<ScannedApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            packageManager.queryIntentActivities(intent, 0)
        } catch (_: RuntimeException) {
            emptyList()
        }
        return resolved.mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (!RootMenu.acceptsPackage(packageName)) return@mapNotNull null
            val enabled = info.activityInfo.enabled && info.activityInfo.applicationInfo.enabled
            val launch = try {
                packageManager.getLaunchIntentForPackage(packageName)
            } catch (_: RuntimeException) {
                null
            }
            if (!enabled || launch == null) return@mapNotNull null
            val label = try {
                info.loadLabel(packageManager)?.toString().orEmpty()
            } catch (_: RuntimeException) {
                ""
            }
            ScannedApp(packageName, RootMenu.cleanLabel(label, packageName))
        }.distinctBy { it.packageName }
    }
}
