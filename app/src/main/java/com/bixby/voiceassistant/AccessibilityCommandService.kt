package com.bixby.voiceassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
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
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    private fun findAndClick(text: String): Boolean {
        val node = findNode(text) ?: return false
        return clickNode(node)
    }

    private fun findAndSetText(text: String): Boolean {
        val node = root()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findEditable(root()) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findAndScroll(forward: Boolean): Boolean {
        val currentRoot = root() ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return findScrollable(currentRoot, action)
    }

    private fun findScrollable(node: AccessibilityNodeInfo?, action: Int): Boolean {
        if (node == null) return false
        if (node.isScrollable && node.isEnabled && node.performAction(action)) return true
        for (i in 0 until node.childCount) if (findScrollable(node.getChild(i), action)) return true
        return false
    }

    private fun findNode(text: String): AccessibilityNodeInfo? {
        val currentRoot = root() ?: return null
        currentRoot.findAccessibilityNodeInfosByText(text).firstOrNull()?.let { return it }
        return findByDescription(currentRoot, text)
    }

    private fun findByDescription(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) {
            val found = findByDescription(node.getChild(i), text)
            if (found != null) return found
        }
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
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.isEnabled) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent = parent.parent
        }
        return false
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Operates Samsung's Quick Settings connectivity tile. Android does not expose
     * modern third-party APIs that directly switch Wi-Fi/Bluetooth, so Accessibility
     * is used to perform the same user action on the system tile.
     */
    private fun setTileState(tile: String, enable: Boolean): Boolean {
        if (instance == null) return false
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        // One UI can expose the panel tree a little later than the global action.
        // Retry instead of assuming the first accessibility tree is complete.
        handler.postDelayed({ tryClickSystemTile(tile, enable, 0) }, 650L)
        return true
    }

    private fun tryClickSystemTile(tile: String, enable: Boolean, attempt: Int) {
        val node = findSystemTile(root(), tile)
        if (node != null) {
            val changed = clickOnlyIfNeeded(node, enable)
            if (changed) {
                handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 450L)
                return
            }
            // Already in the requested state: close the panel without toggling it.
            if (isTileInRequestedState(node, enable)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }
        if (attempt < 4) {
            handler.postDelayed({ tryClickSystemTile(tile, enable, attempt + 1) }, 350L)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun findSystemTile(node: AccessibilityNodeInfo?, tile: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val labels = listOf(node.text?.toString(), node.contentDescription?.toString())
            .filterNotNull().joinToString(" ")
        val normalized = labels.lowercase()
        val match = when (tile.lowercase()) {
            "wi-fi", "wifi" -> normalized.contains("wi-fi") || normalized.contains("wifi") || normalized.contains("वाई-फाई") || normalized.contains("वाईफाई")
            "bluetooth" -> normalized.contains("bluetooth") || normalized.contains("ब्लूटूथ")
            else -> normalized.contains(tile.lowercase())
        }
        if (match && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            findSystemTile(node.getChild(i), tile)?.let { return it }
        }
        return null
    }

    private fun clickOnlyIfNeeded(node: AccessibilityNodeInfo, enable: Boolean): Boolean {
        if (!node.isEnabled) return false
        if (node.isCheckable) {
            if (node.isChecked == enable) return false
            return clickNode(node)
        }
        val state = nodeState(node)
        if (state == enable) return false
        if (state == null) {
            // Unknown state: do not blindly toggle a connectivity control.
            return false
        }
        return clickNode(node)
    }

    private fun isTileInRequestedState(node: AccessibilityNodeInfo, enable: Boolean): Boolean = nodeState(node) == enable

    private fun nodeState(node: AccessibilityNodeInfo): Boolean? {
        if (node.isCheckable) return node.isChecked
        val text = listOf(node.text?.toString(), node.contentDescription?.toString())
            .filterNotNull().joinToString(" ").lowercase()
        val off = text.contains("off") || text.contains("disabled") || text.contains("बंद") || text.contains("चालू नहीं")
        val on = text.contains("on") || text.contains("enabled") || text.contains("चालू")
        return when {
            on && !off -> true
            off && !on -> false
            else -> null
        }
    }
}
