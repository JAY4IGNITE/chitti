package com.owlcoders.chitti.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.owlcoders.chitti.db.AppDatabase
import com.owlcoders.chitti.db.entities.UserProfile
import com.owlcoders.chitti.ui.components.ChittiSurfaceCard
import com.owlcoders.chitti.ui.components.ChittiTextField
import com.owlcoders.chitti.ui.components.PrimaryButton
import com.owlcoders.chitti.ui.components.ScreenHeader
import com.owlcoders.chitti.ui.components.ScreenScaffold
import com.owlcoders.chitti.ui.components.SecondaryButton
import com.owlcoders.chitti.ui.components.SectionLabel
import com.owlcoders.chitti.ui.components.Space
import com.owlcoders.chitti.ui.components.StatusPill
import com.owlcoders.chitti.ui.components.staggeredEntrance
import com.owlcoders.chitti.ui.theme.Accent
import com.owlcoders.chitti.ui.theme.Mint
import com.owlcoders.chitti.ui.theme.TextHigh
import com.owlcoders.chitti.ui.theme.TextMid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    ScreenScaffold {
        ScreenHeader(
            title = "Profile",
            subtitle = "Used by Chitti's autofill to complete forms for you"
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                SectionLabel("Your details")
                ChittiSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                        .staggeredEntrance(0)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        ChittiTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            placeholder = "First name",
                            label = "First name",
                            leadingIcon = Icons.Filled.Person
                        )
                        ChittiTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            placeholder = "Last name",
                            label = "Last name",
                            leadingIcon = Icons.Filled.Person
                        )
                        ChittiTextField(
                            value = email,
                            onValueChange = { email = it },
                            placeholder = "you@example.com",
                            label = "Email",
                            leadingIcon = Icons.Filled.AlternateEmail
                        )
                        ChittiTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            placeholder = "Phone number",
                            label = "Phone",
                            leadingIcon = Icons.Filled.Phone
                        )
                        ChittiTextField(
                            value = dob,
                            onValueChange = { dob = it },
                            placeholder = "DD/MM/YYYY",
                            label = "Date of birth",
                            leadingIcon = Icons.Filled.Cake
                        )
                        ChittiTextField(
                            value = address,
                            onValueChange = { address = it },
                            placeholder = "Street, city, postcode",
                            label = "Address",
                            leadingIcon = Icons.Filled.Home,
                            singleLine = false
                        )
                    }

                    Spacer(Modifier.height(Space.l))

                    PrimaryButton(
                        text = "Save profile",
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
                        fill = true
                    )

                    AnimatedVisibility(
                        visible = showSavedMessage,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.m),
                            contentAlignment = Alignment.Center
                        ) {
                            StatusPill(text = "Profile saved", tint = Mint, icon = Icons.Filled.Check)
                        }
                    }
                }

                SectionLabel("Autofill")
                ChittiSurfaceCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                        .staggeredEntrance(1)
                ) {
                    Text(
                        text = "Chitti Autofill",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextHigh
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        text = "Set Chitti as your system autofill service and it will fill these details into forms in other apps. The details never leave this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMid
                    )
                    Spacer(Modifier.height(Space.l))
                    SecondaryButton(
                        text = "Open autofill settings",
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
                        fill = true
                    )
                }
            }
        }
    }
}
