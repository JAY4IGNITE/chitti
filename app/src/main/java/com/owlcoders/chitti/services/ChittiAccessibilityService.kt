package com.owlcoders.chitti.services

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChittiAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ChittiAccessibilityService? = null
            private set

        private const val TAG = "ChittiA11y"

        /** How long we wait for the user to bring the target app to the front. */
        private const val SWITCH_WAIT_MS = 10_000L
        private const val POLL_MS = 500L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Driven by explicit commands; events are not needed.
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Types [text] into the focused (or first) editable field of the app in front.
     *
     * The command is issued from inside Chitti's own UI, so the active window at that moment is
     * Chitti itself. We therefore wait up to [SWITCH_WAIT_MS] for the user to switch to the app
     * they want to type in, then inject the text there. Never types into Chitti's own window.
     */
    fun typeTextGlobal(text: String, executeSend: Boolean = false) {
        serviceScope.launch {
            var root: AccessibilityNodeInfo? = null
            var waited = 0L
            while (waited <= SWITCH_WAIT_MS) {
                val candidate = rootInActiveWindow
                if (candidate != null && candidate.packageName != null && candidate.packageName != packageName) {
                    root = candidate
                    break
                }
                delay(POLL_MS)
                waited += POLL_MS
            }

            if (root == null) {
                Log.w(TAG, "No foreign app came to the foreground within ${SWITCH_WAIT_MS}ms; not typing.")
                toast("Chitti: switch to the app you want to type in, then try again.")
                return@launch
            }

            val editableNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
                ?: findFirstEditableNode(root)

            if (editableNode == null) {
                Log.e(TAG, "No editable node found to type into (${root.packageName}).")
                toast("Chitti: no text field found in ${root.packageName}.")
                return@launch
            }

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val ok = editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d(TAG, "Injected text into ${root.packageName}: ok=$ok")

            if (ok && executeSend) {
                delay(500) // Small delay to let the UI update
                val sendButton = findSendButton(rootInActiveWindow ?: root)
                if (sendButton != null) {
                    sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "Clicked send button")
                }
            }
        }
    }

    private fun toast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.w(TAG, "Toast failed: ${e.message}")
        }
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val editable = findFirstEditableNode(child)
            if (editable != null) return editable
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        // Basic heuristic for find send button
        if (node.isClickable) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val txt = node.text?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""
            if (desc.contains("send") || desc.contains("pampinchu") || txt == "send" || id.contains("send")) return node
        }
        for (i in 0 until node.childCount) {
            val found = findSendButton(node.getChild(i))
            if (found != null) return found
        }
        return null
    }
}
