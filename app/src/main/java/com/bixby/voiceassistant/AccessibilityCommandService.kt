package com.bixby.voiceassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
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
            ?: findEditable(root())
            ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findAndScroll(forward: Boolean): Boolean {
        val nodes = root()?.findAccessibilityNodeInfosByViewId("android:id/list").orEmpty()
        val target = nodes.firstOrNull() ?: root() ?: return false
        return target.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
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
}
