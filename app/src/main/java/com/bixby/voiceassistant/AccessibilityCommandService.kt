package com.bixby.voiceassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo

class AccessibilityCommandService : AccessibilityService() {
    companion object {
        @Volatile private var instance: AccessibilityCommandService? = null
        fun isEnabled(): Boolean = instance != null
        fun global(action: Int): Boolean = instance?.performGlobalAction(action) == true
        fun clickText(text: String): Boolean = instance?.findAndClick(text) == true
        fun setText(text: String): Boolean = instance?.findAndSetText(text) == true
        fun scroll(forward: Boolean): Boolean = instance?.findAndScroll(forward) == true
        fun tap(x: Float, y: Float): Boolean = instance?.tapAt(x, y) == true
        fun setSystemTile(tile: String, enable: Boolean): Boolean = instance?.setTileState(tile, enable) == true
    }

    private val handler = Handler(Looper.getMainLooper())
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); if (instance === this) instance = null; super.onDestroy() }
    private fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    private fun findAndClick(text: String): Boolean = findNode(text)?.let { clickNode(it) } ?: false
    private fun findAndSetText(text: String): Boolean {
        val node = root()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root()) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
    }
    private fun findAndScroll(forward: Boolean): Boolean = root()?.let { findScrollable(it, if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) } ?: false
    private fun findScrollable(node: AccessibilityNodeInfo?, action: Int): Boolean {
        if (node == null) return false
        if (node.isScrollable && node.isEnabled && node.performAction(action)) return true
        for (i in 0 until node.childCount) if (findScrollable(node.getChild(i), action)) return true
        return false
    }
    private fun findNode(text: String): AccessibilityNodeInfo? {
        val r = root() ?: return null
        r.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { return it }
        return findByDescription(r, text)
    }
    private fun findByDescription(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.contains(text, true) == true) return node
        for (i in 0 until node.childCount) findByDescription(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) findEditable(node.getChild(i))?.let { return it }
        return null
    }
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.isEnabled) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var p = node.parent
        while (p != null) { if (p.isClickable && p.isEnabled) return p.performAction(AccessibilityNodeInfo.ACTION_CLICK); p = p.parent }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && node.isVisibleToUser) {
            val b = android.graphics.Rect(); node.getBoundsInScreen(b); tapAt(b.centerX().toFloat(), b.centerY().toFloat())
        } else false
    }
    private fun tapAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 70)).build(), null, null)
    }

    /** Never sends a blind BACK after touching Quick Settings: BACK can fall through to the
     * assistant Activity and make it appear to close. One UI also needs extra time to expose
     * the tile to Accessibility, so use delayed retries and verify the resulting state. */
    private fun setTileState(tile: String, enable: Boolean): Boolean {
        if (instance == null) return false
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        handler.postDelayed({ tryClickSystemTile(tile, enable, 0) }, 900L)
        return true
    }

    private fun tryClickSystemTile(tile: String, enable: Boolean, attempt: Int) {
        val node = findSystemTile(root(), tile)
        if (node != null) {
            val state = nodeState(node)
            if (state == enable) return
            if (clickNode(node)) {
                handler.postDelayed({ verifyTileState(tile, enable, 0) }, 900L)
                return
            }
        }
        if (attempt < 6) handler.postDelayed({ tryClickSystemTile(tile, enable, attempt + 1) }, 350L)
    }

    private fun verifyTileState(tile: String, enable: Boolean, attempt: Int) {
        val node = findSystemTile(root(), tile)
        if (node != null && nodeState(node) == enable) return
        if (attempt < 2) handler.postDelayed({ verifyTileState(tile, enable, attempt + 1) }, 500L)
    }

    private fun findSystemTile(node: AccessibilityNodeInfo?, tile: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val labels = listOf(node.text?.toString(), node.contentDescription?.toString(), if (Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString() else null).filterNotNull().joinToString(" ").lowercase()
        val match = when (tile.lowercase()) {
            "wi-fi", "wifi" -> labels.contains("wi-fi") || labels.contains("wifi") || labels.contains("internet") || labels.contains("वाई-फाई") || labels.contains("वाईफाई")
            "bluetooth" -> labels.contains("bluetooth") || labels.contains("ब्लूटूथ")
            else -> labels.contains(tile.lowercase())
        }
        if (match && node.isEnabled) return node
        for (i in 0 until node.childCount) findSystemTile(node.getChild(i), tile)?.let { return it }
        return null
    }

    private fun nodeState(node: AccessibilityNodeInfo): Boolean? {
        if (node.isCheckable) return node.isChecked
        val text = listOf(node.text?.toString(), node.contentDescription?.toString(), if (Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString() else null).filterNotNull().joinToString(" ").lowercase()
        val off = text.contains("off") || text.contains("disabled") || text.contains("not connected") || text.contains("बंद") || text.contains("चालू नहीं")
        val on = text.contains("on") || text.contains("enabled") || text.contains("connected") || text.contains("चालू")
        return when { on && !off -> true; off && !on -> false; else -> null }
    }
}
