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
        if (viewNode.autofillHints != null && viewNode.autofillHints!!.isNotEmpty()) {
            val hints = viewNode.autofillHints!!
            val id = viewNode.autofillId
            if (id != null) {
                parsedFields[hints[0].lowercase()] = id
            }
        }
        
        for (i in 0 until viewNode.childCount) {
            val childNode: AssistStructure.ViewNode = viewNode.getChildAt(i)
            traverseNode(childNode, parsedFields)
        }
    }

    private fun matchHintToProfile(hint: String, profile: UserProfile): String? {
        return when {
            hint.contains("name") && hint.contains("first") -> profile.firstName
            hint.contains("name") && hint.contains("last") -> profile.lastName
            hint.contains("name") -> "${profile.firstName} ${profile.lastName}".trim()
            hint.contains("email") -> profile.email
            hint.contains("phone") -> profile.phoneNumber
            hint.contains("address") -> profile.address
            hint.contains("birth") || hint.contains("dob") -> profile.dateOfBirth
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
