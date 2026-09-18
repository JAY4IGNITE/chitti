package com.owlcoders.chitti.services

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChittiAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ChittiAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d("ChittiA11y", "Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We can listen to events here if needed, but for now we are driving it via commands.
    }

    override fun onInterrupt() {
        Log.d("ChittiA11y", "Service Interrupted")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    fun typeTextGlobal(text: String, executeSend: Boolean = false) {
        CoroutineScope(Dispatchers.Main).launch {
            val rootNode = rootInActiveWindow ?: return@launch
            val editableNode = findFirstEditableNode(rootNode)
            
            if (editableNode != null) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                editableNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                Log.d("ChittiA11y", "Injected text: $text")
                
                if (executeSend) {
                    delay(500) // Small delay to let the UI update
                    val sendButton = findSendButton(rootNode)
                    if (sendButton != null) {
                        sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d("ChittiA11y", "Clicked send button")
                    }
                }
            } else {
                Log.e("ChittiA11y", "No editable node found to type into.")
            }
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
            val text = node.text?.toString()?.lowercase() ?: ""
            if (desc.contains("send") || text.contains("send") || desc.contains("pampinchu")) {
                return node
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val btn = findSendButton(child)
            if (btn != null) return btn
        }
        return null
    }
}
