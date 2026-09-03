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
        @Volatile
        private var instance: AccessibilityCommandService? = null

        fun isEnabled(): Boolean = instance != null
        fun global(action: Int): Boolean = instance?.performGlobalAction(action) == true
        fun clickText(text: String): Boolean = instance?.findAndClick(text) == true
        fun setText(text: String): Boolean = instance?.findAndSetText(text) == true
        fun scroll(forward: Boolean): Boolean = instance?.findAndScroll(forward) == true
        fun tap(x: Float, y: Float): Boolean = instance?.tapAt(x, y) == true

        /** Directly operates the Android/Samsung Quick Settings tile through Accessibility. */
        fun setSystemTile(tile: String, enable: Boolean): Boolean = instance?.setTileState(tile, enable) == true
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

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
        val node = root()?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findEditable(root()) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findAndScroll(forward: Boolean): Boolean {
        val currentRoot = root() ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
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
        val exact = currentRoot.findAccessibilityNodeInfosByText(text).firstOrNull()
        if (exact != null) return exact
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
        for (i in 0 until node.childCount) {
            val found = findEditable(node.getChild(i))
            if (found != null) return found
        }
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
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun setTileState(tile: String, enable: Boolean): Boolean {
        // Android does not allow a normal third-party app to call the Wi-Fi/Bluetooth
        // enable APIs directly on modern Android. Accessibility can operate the
        // Samsung Quick Settings tile instead, without opening the Settings app.
        if (instance == null) return false
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        handler.postDelayed({
            val node = findTile(root(), tile)
            if (node != null && shouldClickTile(node, enable)) clickNode(node)
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 250L)
        }, 550L)
        return true
    }

    private fun findTile(node: AccessibilityNodeInfo?, tile: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val label = listOf(node.text?.toString(), node.contentDescription?.toString())
            .filterNotNull().joinToString(" ")
        if (label.contains(tile, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val found = findTile(node.getChild(i), tile)
            if (found != null) return found
        }
        return null
    }

    private fun shouldClickTile(node: AccessibilityNodeInfo, enable: Boolean): Boolean {
        if (!node.isEnabled) return false
        if (node.isCheckable) return node.isChecked != enable
        val stateText = listOf(node.text?.toString(), node.contentDescription?.toString())
            .filterNotNull().joinToString(" ").lowercase()
        val isOff = stateText.contains("off") || stateText.contains("बंद") || stateText.contains("चालू नहीं")
        val isOn = stateText.contains("on") || stateText.contains(" चालू") || stateText.contains("enabled")
        return if (enable) isOff && !isOn else isOn && !isOff
    }
}
