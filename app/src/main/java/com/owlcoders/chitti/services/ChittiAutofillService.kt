package com.owlcoders.chitti.services

import android.app.assist.AssistStructure
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillContext
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.owlcoders.chitti.R
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.db.entities.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChittiAutofillService : AutofillService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val TAG = "ChittiAutofill"

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure: AssistStructure = request.fillContexts.last().structure
        val parsedFields = mutableMapOf<String, AutofillId>()

        // Traverse AssistStructure to find fields
        traverseStructure(structure, parsedFields)

        if (parsedFields.isEmpty()) {
            callback.onSuccess(null)
            return
        }

        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val profile = db.userProfileDao().getUserProfileSync()

            if (profile != null) {
                val responseBuilder = FillResponse.Builder()
                val datasetBuilder = Dataset.Builder()

                // Create a basic presentation for the autofill dropdown
                val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
                presentation.setTextViewText(android.R.id.text1, "Chitti Autofill")

                var hasSetAtLeastOneField = false

                parsedFields.forEach { (hint, autofillId) ->
                    val valueToFill = matchHintToProfile(hint, profile)
                    if (valueToFill != null) {
                        datasetBuilder.setValue(autofillId, AutofillValue.forText(valueToFill), presentation)
                        hasSetAtLeastOneField = true
                    }
                }

                if (hasSetAtLeastOneField) {
                    responseBuilder.addDataset(datasetBuilder.build())
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(responseBuilder.build())
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        callback.onSuccess(null)
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback.onSuccess(null)
                }
            }
        }
    }

    private fun traverseStructure(structure: AssistStructure, parsedFields: MutableMap<String, AutofillId>) {
        val windowNodes: Int = structure.windowNodeCount
        for (i in 0 until windowNodes) {
            val windowNode: AssistStructure.WindowNode = structure.getWindowNodeAt(i)
            val viewNode: AssistStructure.ViewNode = windowNode.rootViewNode
            traverseNode(viewNode, parsedFields)
        }
    }

    private fun traverseNode(viewNode: AssistStructure.ViewNode, parsedFields: MutableMap<String, AutofillId>) {
        val hints = viewNode.autofillHints
        if (hints != null && hints.isNotEmpty()) {
            val id = viewNode.autofillId
            if (id != null) {
                // Two fields with the same hint (e.g. "name" twice, or email + confirm email) must
                // both be filled, so key by hint plus a unique suffix instead of overwriting.
                var key = hints[0].lowercase()
                var n = 1
                while (parsedFields.containsKey(key)) {
                    key = "${hints[0].lowercase()}#${n++}"
                }
                parsedFields[key] = id
            }
        }

        for (i in 0 until viewNode.childCount) {
            val childNode: AssistStructure.ViewNode = viewNode.getChildAt(i)
            traverseNode(childNode, parsedFields)
        }
    }

    private fun matchHintToProfile(rawHint: String, profile: UserProfile): String? {
        val hint = rawHint.substringBefore('#') // strip the duplicate suffix added in traverseNode
        fun String.orNull() = takeIf { it.isNotBlank() }
        return when {
            // Credentials are never ours to fill.
            hint.contains("username") || hint.contains("password") || hint.contains("otp") ||
                hint.contains("securitycode") || hint.contains("verificationcode") || hint.contains("smscode") ||
                hint.contains("postal") || hint.contains("zip") -> null // we hold no separate ZIP; never stuff the full address into it
            hint.contains("given") || (hint.contains("name") && hint.contains("first")) -> profile.firstName.orNull()
            hint.contains("family") || (hint.contains("name") && hint.contains("last")) -> profile.lastName.orNull()
            hint.contains("name") -> "${profile.firstName} ${profile.lastName}".trim().orNull()
            hint.contains("email") -> profile.email.orNull()
            hint.contains("phone") || hint.contains("tel") -> profile.phoneNumber.orNull()
            hint.contains("address") || hint.contains("street") -> profile.address.orNull()
            hint.contains("birth") || hint.contains("dob") -> profile.dateOfBirth.orNull()
            else -> null
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
