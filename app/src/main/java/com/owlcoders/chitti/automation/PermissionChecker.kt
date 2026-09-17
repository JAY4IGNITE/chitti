package com.owlcoders.chitti.automation

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Checks whether required permissions are granted for a given action.
 */
object PermissionChecker {

    /**
     * Returns true if all required permissions for the given action are granted.
     */
    fun hasPermissions(context: Context, action: ActionId): Boolean {
        return action.requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Returns the list of missing permissions for a given action.
     */
    fun getMissingPermissions(context: Context, action: ActionId): List<String> {
        return action.requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks a specific permission string.
     */
    fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
