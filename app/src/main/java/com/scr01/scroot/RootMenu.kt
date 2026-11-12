package com.scr01.scroot

/**
 * Editable Home Root menu. This file does not start an exploit, uninstall an
 * app, or turn a saved label into a shell command.
 */
internal data class ScannedApp(
    val packageName: String,
    val label: String
)

internal data class MenuEntry(
    val packageName: String,
    val label: String
)

internal data class MenuCategory(
    val id: String,
    val name: String,
    val entries: List<MenuEntry>
)

internal data class MenuDocument(
    val categories: List<MenuCategory>
)

internal data class VisibleCategory(
    val name: String,
    val entries: List<MenuEntry>
)

internal enum class MenuSync {
    UNCONFIRMED,
    BRIDGE_CURRENT,
    BRIDGE_OLD
}

internal object RootMenu {
    const val PREFS_NAME = "root_menu"
    const val KEY_DOCUMENT = "menu_document"
    const val PROVIDER_AUTHORITY = "com.scr01.scroot.menu"
    const val HOME_PACKAGE = "com.samsung.android.mhshome"
    const val BOX_PACKAGE = "com.box.app"
    const val MENU_BRIDGE_SHA256 =
        "70899a6ac234d9ea4a902f62d653003a996ef2cce8595784d6fa3cd9f68b96cb"

    private val packagePattern = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    val defaultCategoryNames: List<Pair<String, String>> = listOf(
        "clocks" to "时钟",
        "keys" to "按键与手势",
        "proxy" to "透明代理",
        "root" to "Root工具",
        "launchers" to "启动器"
    )

    private val knownLabels = mapOf(
        "com.android.deskclock" to "时钟",
        "com.zwh.flip.clock" to "翻页时钟",
        "one.alynx.flipclock" to "Alynx 时钟",
        BOX_PACKAGE to "Box for Root",
        "com.follow.clash" to "FlClash",
        "com.github.surfing" to "SurfingTile",
        "com.scr01.scroot" to "SCRoot",
        "com.rifsxd.ksunext" to "KSU Next",
        "com.microsoft.launcher" to "微软启动器"
    )

    fun acceptsPackage(packageName: String): Boolean =
        packageName.length in 3..120 && packagePattern.matches(packageName)

    fun cleanLabel(raw: String, fallback: String): String {
        val cleaned = raw
            .replace('\u001f', ' ')
            .replace('\u0000', ' ')
            .filter { it == '\n' || it == '\r' || it == '\t' || it.code >= 32 }
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .trim()
            .take(40)
        return cleaned.ifBlank { fallback.take(40) }
    }

    fun menuKeyMatch(app: ScannedApp): Boolean {
        val text = "${app.label} ${app.packageName}".lowercase()
        return "menukey" in text || "menu key" in text || "menukey" in app.packageName.lowercase()
    }

    fun withDefaults(stored: MenuDocument?, launchable: List<ScannedApp>): MenuDocument {
        if (stored != null) return stored
        val byPackage = launchable.filter { acceptsPackage(it.packageName) }.associateBy { it.packageName }
        fun entry(packageName: String): MenuEntry? {
            val app = byPackage[packageName] ?: return null
            return MenuEntry(packageName, cleanLabel(app.label, knownLabels[packageName] ?: app.label))
        }
        val clocks = listOfNotNull(
            entry("com.android.deskclock"),
            entry("com.zwh.flip.clock"),
            entry("one.alynx.flipclock")
        )
        val keys = launchable.filter { acceptsPackage(it.packageName) && menuKeyMatch(it) }
            .map { MenuEntry(it.packageName, cleanLabel(it.label, it.label)) }
        val proxy = listOfNotNull(
            entry(BOX_PACKAGE),
            entry("com.follow.clash"),
            entry("com.github.surfing")
        )
        val root = listOfNotNull(
            entry("com.scr01.scroot"),
            entry("com.rifsxd.ksunext")
        )
        val launchers = listOfNotNull(entry("com.microsoft.launcher"))
        val seeded = listOf(
            MenuCategory("clocks", "时钟", clocks),
            MenuCategory("keys", "按键与手势", keys),
            MenuCategory("proxy", "透明代理", proxy),
            MenuCategory("root", "Root工具", root),
            MenuCategory("launchers", "启动器", launchers)
        )
        return MenuDocument(seeded)
    }

    fun visible(document: MenuDocument, launchablePackages: Set<String>): List<VisibleCategory> =
        document.categories.mapNotNull { category ->
            val name = cleanLabel(category.name, "分类")
            val entries = category.entries.filter {
                acceptsPackage(it.packageName) && it.packageName in launchablePackages
            }
            if (entries.isEmpty()) null else VisibleCategory(name, entries)
        }

    fun removeEntry(document: MenuDocument, categoryId: String, packageName: String): MenuDocument =
        document.copy(categories = document.categories.map { category ->
            if (category.id != categoryId) category
            else category.copy(entries = category.entries.filterNot { it.packageName == packageName })
        })

    fun moveCategory(document: MenuDocument, index: Int, delta: Int): MenuDocument {
        val next = document.categories.toMutableList()
        val target = index + delta
        if (index !in next.indices || target !in next.indices) return document
        val item = next.removeAt(index)
        next.add(target, item)
        return document.copy(categories = next)
    }

    fun moveEntry(document: MenuDocument, categoryId: String, index: Int, delta: Int): MenuDocument =
        document.copy(categories = document.categories.map { category ->
            if (category.id != categoryId) category
            else {
                val next = category.entries.toMutableList()
                val target = index + delta
                if (index !in next.indices || target !in next.indices) category
                else {
                    val item = next.removeAt(index)
                    next.add(target, item)
                    category.copy(entries = next)
                }
            }
        })

    fun renameCategory(document: MenuDocument, categoryId: String, name: String): MenuDocument =
        document.copy(categories = document.categories.map {
            if (it.id == categoryId) it.copy(name = cleanLabel(name, it.name)) else it
        })

    fun renameEntry(document: MenuDocument, categoryId: String, packageName: String, label: String): MenuDocument =
        document.copy(categories = document.categories.map { category ->
            if (category.id != categoryId) category
            else category.copy(entries = category.entries.map {
                if (it.packageName == packageName) it.copy(label = cleanLabel(label, it.label)) else it
            })
        })

    fun addCategory(document: MenuDocument, id: String, name: String): MenuDocument =
        document.copy(categories = document.categories + MenuCategory(id, cleanLabel(name, "新分类"), emptyList()))

    fun addEntry(document: MenuDocument, categoryId: String, app: ScannedApp): MenuDocument {
        if (!acceptsPackage(app.packageName)) return document
        return document.copy(categories = document.categories.map { category ->
            if (category.id != categoryId) category
            else if (category.entries.any { it.packageName == app.packageName }) category
            else category.copy(
                entries = category.entries + MenuEntry(
                    app.packageName,
                    cleanLabel(app.label, knownLabels[app.packageName] ?: app.label)
                )
            )
        })
    }

    fun encode(document: MenuDocument): String = buildString {
        append("1\n")
        document.categories.forEach { category ->
            append("C\t").append(escape(category.id)).append('\t').append(escape(category.name)).append('\n')
            category.entries.forEach { entry ->
                if (acceptsPackage(entry.packageName)) {
                    append("E\t").append(escape(entry.packageName)).append('\t')
                        .append(escape(entry.label)).append('\n')
                }
            }
        }
    }

    fun decode(raw: String?): MenuDocument? {
        if (raw.isNullOrBlank()) return null
        val lines = raw.split('\n')
        if (lines.firstOrNull()?.trim() != "1") return null
        val categories = mutableListOf<MenuCategory>()
        var current: MenuCategory? = null
        lines.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "C" -> {
                    current?.let { categories += it }
                    if (parts.size < 3) return null
                    current = MenuCategory(unescape(parts[1]), unescape(parts[2]), emptyList())
                }
                "E" -> {
                    val open = current ?: return null
                    if (parts.size < 3) return null
                    val packageName = unescape(parts[1])
                    if (!acceptsPackage(packageName)) return@forEach
                    current = open.copy(
                        entries = open.entries + MenuEntry(packageName, unescape(parts[2]))
                    )
                }
                else -> return null
            }
        }
        current?.let { categories += it }
        return MenuDocument(categories)
    }

    fun syncState(mountedHash: String?, expectedHash: String): MenuSync = when {
        mountedHash == null -> MenuSync.UNCONFIRMED
        mountedHash == expectedHash -> MenuSync.BRIDGE_CURRENT
        else -> MenuSync.BRIDGE_OLD
    }

    fun syncMessage(state: MenuSync): String = when (state) {
        MenuSync.UNCONFIRMED ->
            "菜单已保存在本机。现在无法确认侧栏是否已升级，所以不会显示成已经生效。"
        MenuSync.BRIDGE_CURRENT ->
            "侧栏已能读取这份菜单。重新打开侧栏即可看到，不用重启，也不会重新 root。"
        MenuSync.BRIDGE_OLD ->
            "菜单已保存。侧栏还是旧版本，需要在主页点一次「修复启动器」。这一步不会重新 root。"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var escaped = false
        value.forEach { char ->
            if (escaped) {
                out.append(when (char) {
                    't' -> '\t'
                    'n' -> '\n'
                    else -> char
                })
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                out.append(char)
            }
        }
        return out.toString()
    }
}
