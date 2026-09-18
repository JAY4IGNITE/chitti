package com.owlcoders.chitti.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.db.entities.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var showSavedMessage by remember { mutableStateOf(false) }

    // Load initial profile data. getUserProfileSync() is a suspend DAO call, so Room
    // already runs it on its own executor; the Compose state writes stay on Main.
    LaunchedEffect(Unit) {
        val profile = db.userProfileDao().getUserProfileSync()
        if (profile != null) {
            firstName = profile.firstName
            lastName = profile.lastName
            email = profile.email
            phoneNumber = profile.phoneNumber
            address = profile.address
            dob = profile.dateOfBirth
        }
        isLoading = false
    }

    // Auto-hide the "saved" confirmation instead of leaving it on screen forever.
    LaunchedEffect(showSavedMessage) {
        if (showSavedMessage) {
            delay(2500)
            showSavedMessage = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Personal Profile") })
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Chitti will use these details to help you auto-fill forms securely.",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val newProfile = UserProfile(
                                id = 1,
                                firstName = firstName,
                                lastName = lastName,
                                email = email,
                                phoneNumber = phoneNumber,
                                address = address,
                                dateOfBirth = dob
                            )
                            db.userProfileDao().insertOrUpdateProfile(newProfile)
                            showSavedMessage = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Profile")
                }

                if (showSavedMessage) {
                    Text(
                        text = "Profile saved successfully!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enable system-wide Autofill to let Chitti fill forms inside other apps.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                        intent.data = android.net.Uri.parse("package:${context.packageName}")
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            } catch (e2: Exception) {
                                android.widget.Toast.makeText(context, "Could not open system settings", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Enable Chitti Autofill")
                }
            }
        }
    }
}
