package com.scr01.scroot

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
/**
 * Home reads the saved menu here. Callers other than MHSHome and this app
 * receive an empty cursor. The provider does not start apps or accept writes.
 */
class RootMenuProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(arrayOf("category", "package", "label"))
        val appContext = context ?: return cursor
        if (!callerAllowed(
                appContext.packageManager.getPackagesForUid(Binder.getCallingUid()),
                appContext.packageName
            )
        ) return cursor
        val store = RootMenuStore(appContext)
        val launchable = InstalledApps.scan(appContext.packageManager).map { it.packageName }.toSet()
        RootMenu.visible(store.current(InstalledApps.scan(appContext.packageManager)), launchable)
            .forEach { category ->
                category.entries.forEach { entry ->
                    cursor.addRow(arrayOf(category.name, entry.packageName, entry.label))
                }
            }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.scr01.menu"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

}

internal fun callerAllowed(packagesForUid: Array<String>?, ownPackage: String): Boolean =
    packagesForUid?.any { it == RootMenu.HOME_PACKAGE || it == ownPackage } == true
