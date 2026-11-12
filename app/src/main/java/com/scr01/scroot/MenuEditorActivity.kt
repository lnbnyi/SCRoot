package com.scr01.scroot

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Edits the saved Root menu. It does not uninstall apps, start proxies, or
 * run the kernel exploit.
 */
class MenuEditorActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: RootMenuStore
    private lateinit var document: MenuDocument
    private var launchable: List<ScannedApp> = emptyList()
    private var syncText: String = RootMenu.syncMessage(MenuSync.UNCONFIRMED)
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RootMenuStore(this)
        launchable = InstalledApps.scan(packageManager)
        document = store.current(launchable)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val background = 0xFFF4F1EA.toInt()
        val textPrimary = 0xFF1C1915.toInt()
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(background)
            addView(content)
        })
        render(textPrimary)
        refreshSync()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun refreshSync() {
        executor.execute {
            val hash = readMountedHomeHash()
            val message = RootMenu.syncMessage(
                RootMenu.syncState(hash, RootMenu.MENU_BRIDGE_SHA256)
            )
            runOnUiThread {
                syncText = message
                if (!isDestroyed) render(0xFF1C1915.toInt())
            }
        }
    }

    private fun render(textPrimary: Int) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val primary = 0xFF8A5A2A.toInt()
        content.removeAllViews()
        content.addView(label("Root 菜单", 22, textPrimary, true))
        content.addView(label("只改侧栏里的按钮。不会卸载应用，也不会启动代理。", 14, 0xFF5C564C.toInt(), false))
        content.addView(label(syncText, 14, primary, false))
        content.addView(action("刷新已安装应用", primary) {
            launchable = InstalledApps.scan(packageManager)
            render(textPrimary)
        })
        content.addView(action("新建分类", primary) { prompt("分类名称", "新分类") { addCategory(it) } })
        val visiblePackages = launchable.map { it.packageName }.toSet()
        document.categories.forEachIndexed { categoryIndex, category ->
            content.addView(label(category.name, 18, textPrimary, true))
            content.addView(row(
                action("改名", primary) { prompt("分类名称", category.name) { renameCategory(category.id, it) } },
                action("上移", primary) { update(RootMenu.moveCategory(document, categoryIndex, -1)) },
                action("下移", primary) { update(RootMenu.moveCategory(document, categoryIndex, 1)) },
                action("添加", primary) { pickApp(category.id) }
            ))
            if (category.entries.isEmpty()) {
                content.addView(label("这个分类还是空的，侧栏里不会显示。", 13, 0xFF5C564C.toInt(), false))
            }
            category.entries.forEachIndexed { entryIndex, entry ->
                val shown = entry.packageName in visiblePackages
                content.addView(label(
                    if (shown) entry.label else "${entry.label}（当前未安装，侧栏隐藏）",
                    15,
                    if (shown) textPrimary else 0xFF8A8175.toInt(),
                    false
                ))
                content.addView(row(
                    action("改名", primary) {
                        prompt("按钮名称", entry.label) { renameEntry(category.id, entry.packageName, it) }
                    },
                    action("上移", primary) {
                        update(RootMenu.moveEntry(document, category.id, entryIndex, -1))
                    },
                    action("下移", primary) {
                        update(RootMenu.moveEntry(document, category.id, entryIndex, 1))
                    },
                    action("移除", primary) {
                        update(RootMenu.removeEntry(document, category.id, entry.packageName))
                    }
                ))
            }
        }
    }

    private fun update(next: MenuDocument) {
        document = next
        store.save(document)
        render(0xFF1C1915.toInt())
    }

    private fun addCategory(name: String) {
        update(RootMenu.addCategory(document, UUID.randomUUID().toString(), name))
    }

    private fun renameCategory(id: String, name: String) {
        update(RootMenu.renameCategory(document, id, name))
    }

    private fun renameEntry(categoryId: String, packageName: String, label: String) {
        update(RootMenu.renameEntry(document, categoryId, packageName, label))
    }

    private fun pickApp(categoryId: String) {
        val choices = launchable.sortedBy { it.label.lowercase() }
        if (choices.isEmpty()) return
        val labels = choices.map { it.label + "\n" + it.packageName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("加入侧栏")
            .setItems(labels) { _, which ->
                update(RootMenu.addEntry(document, categoryId, choices[which]))
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun prompt(title: String, initial: String, onValue: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("确定") { _, _ -> onValue(input.text.toString()) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun label(text: String, size: Int, color: Int, title: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size.toFloat()
            setTextColor(color)
            typeface = Typeface.create(
                if (title) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL
            )
            setPadding(0, dp(8), 0, dp(4))
        }

    private fun action(text: String, color: Int, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            textSize = 13f
            isAllCaps = false
            setTextColor(color)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xFFF7E7D4.toInt())
            }
            minimumHeight = 0
            minHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).also { it.marginEnd = dp(6); it.bottomMargin = dp(6) }
            setOnClickListener { onClick() }
        }

    private fun row(vararg children: Button): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            children.forEach { addView(it) }
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun readMountedHomeHash(): String? {
        if (!RootFlow.isModuleLoaded()) return null
        return try {
            val process = ProcessBuilder(
                "/system/bin/su",
                "-c",
                "/system/bin/nsenter -t 1 -m -- /system/bin/sha256sum /system/priv-app/MHSHome/MHSHome.apk"
            ).redirectErrorStream(true).start()
            val finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
            val text = process.inputStream.bufferedReader().readText()
            if (!finished) {
                process.destroy()
                null
            } else {
                Regex("[0-9a-f]{64}").find(text)?.value
            }
        } catch (_: Exception) {
            null
        }
    }
}
