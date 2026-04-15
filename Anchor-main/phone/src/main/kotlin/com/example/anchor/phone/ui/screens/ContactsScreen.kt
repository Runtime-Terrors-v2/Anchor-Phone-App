package com.Anchor.watchguardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Anchor.watchguardian.data.model.Contact
import com.Anchor.watchguardian.ui.theme.*
import com.Anchor.watchguardian.viewmodel.ContactsViewModel

/**
 * Priority emergency contacts screen.
 *
 * Mirrors ContactsPage.ets (HarmonyOS) with:
 *   - Info banner explaining watch sync
 *   - Add-contact form (name required, phone or openID)
 *   - Scrollable contact list with SMS / APP tags
 *   - Remove button per contact
 *   - Force Sync and Test SMS debug buttons
 *
 * State is managed by ContactsViewModel (SharedPreferences + WearEngine P2p sync).
 */
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onBack:    () -> Unit
) {
    val contacts    by viewModel.contacts.collectAsState()
    var showForm    by remember { mutableStateOf(false) }
    var newName     by remember { mutableStateOf("") }
    var newPhone    by remember { mutableStateOf("") }
    var newOpenID   by remember { mutableStateOf("") }
    var errorMsg    by remember { mutableStateOf("") }

    fun addContact() {
        if (newName.isBlank()) { errorMsg = "Name is required"; return }
        if (newPhone.isBlank() && newOpenID.isBlank()) {
            errorMsg = "Enter a phone number or app user ID"; return
        }
        viewModel.addContact(newName, newPhone, newOpenID)
        newName   = ""; newPhone = ""; newOpenID = ""; showForm = false; errorMsg = ""
    }

    Column(
        modifier            = Modifier
            .fillMaxSize()
            .background(Background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header ---
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = Color(0xFFEEEEEE))
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text("\u2190", fontSize = 20.sp, color = TextPrimary) // ←
                }
                Text("Priority contacts", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            }
            Button(
                onClick = { showForm = !showForm; errorMsg = "" },
                shape   = RoundedCornerShape(8.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = TextPrimary)
            ) {
                Text("+ Add", fontSize = 13.sp)
            }
        }

        // --- Info banner ---
        Row(
            modifier              = Modifier
                .fillMaxWidth(0.9f)
                .background(BlueLight, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Text("i", fontSize = 13.sp, color = Color(0xFF0C447C), fontWeight = FontWeight.Bold)
            Text(
                text     = "ANCHOR: These contacts are synced to your watch. They will be alerted even if your phone is offline.",
                fontSize = 12.sp,
                color    = Color(0xFF185FA5),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding      = PaddingValues(bottom = 40.dp)
        ) {
            // --- Add-contact form ---
            if (showForm) {
                item {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth(0.9f)
                            .background(White, RoundedCornerShape(12.dp))
                            .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Add contact", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

                        FormField(
                            label       = "Name *",
                            value       = newName,
                            placeholder = "e.g. Riya Sharma",
                            onValue     = { newName = it }
                        )
                        FormField(
                            label       = "Phone number (for SMS alerts)",
                            value       = newPhone,
                            placeholder = "e.g. +919876543210",
                            keyboard    = KeyboardType.Phone,
                            onValue     = { newPhone = it }
                        )
                        FormField(
                            label       = "App user ID (for push notifications)",
                            value       = newOpenID,
                            placeholder = "Their Huawei ID openID",
                            onValue     = { newOpenID = it }
                        )

                        if (errorMsg.isNotEmpty()) {
                            Text(errorMsg, fontSize = 12.sp, color = AlertRed)
                        }

                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick  = { showForm = false; errorMsg = ""; newName = ""; newPhone = ""; newOpenID = "" },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape    = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel", color = TextSecond)
                            }
                            Button(
                                onClick  = { addContact() },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape    = RoundedCornerShape(8.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                            ) {
                                Text("Save contact")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // --- Empty state ---
            if (contacts.isEmpty() && !showForm) {
                item {
                    Column(
                        modifier            = Modifier.padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("No contacts yet", fontSize = 16.sp, color = TextSecond)
                        Text(
                            "Add priority contacts who will be\nalerted when your watch disconnects.",
                            fontSize = 13.sp, color = TextMuted
                        )
                        Button(
                            onClick = { showForm = true },
                            shape   = RoundedCornerShape(8.dp),
                            colors  = ButtonDefaults.buttonColors(containerColor = TextPrimary)
                        ) {
                            Text("+ Add first contact")
                        }
                    }
                }
            }

            // --- Contact list ---
            items(contacts, key = { it.id }) { contact ->
                ContactRow(contact = contact, onRemove = { viewModel.removeContact(contact.id) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun FormField(
    label:    String,
    value:    String,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    onValue:  (String) -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 12.sp, color = TextSecond)
        OutlinedTextField(
            value            = value,
            onValueChange    = onValue,
            placeholder      = { Text(placeholder, fontSize = 14.sp, color = TextMuted) },
            modifier         = Modifier.fillMaxWidth().height(52.dp),
            shape            = RoundedCornerShape(8.dp),
            keyboardOptions  = KeyboardOptions(keyboardType = keyboard),
            singleLine       = true,
            colors           = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFEEEEEE),
                focusedBorderColor   = TextPrimary
            )
        )
    }
}

@Composable
private fun ContactRow(contact: Contact, onRemove: () -> Unit) {
    fun initials(name: String): String {
        val parts = name.trim().split(" ")
        return if (parts.size >= 2) "${parts[0][0]}${parts[1][0]}".uppercase()
        else name.take(2).uppercase()
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth(0.9f)
            .background(White, RoundedCornerShape(12.dp))
            .border(0.5.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(44.dp)
                .background(PurpleLight, CircleShape)
        ) {
            Text(initials(contact.name), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF3C3489))
        }

        // Contact info
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (contact.phoneNumber.isNotBlank()) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Chip("SMS", Color(0xFF0F6E56), Color(0xFFE1F5EE))
                    Text(contact.phoneNumber, fontSize = 12.sp, color = TextSecond)
                }
            }
            if (contact.openID.isNotBlank()) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Chip("APP", Color(0xFF3C3489), PurpleLight)
                    Text(
                        text     = contact.openID.take(16) + "...",
                        fontSize = 12.sp,
                        color    = TextSecond
                    )
                }
            }
        }

        // Remove button
        TextButton(onClick = onRemove) {
            Text("\u2715", fontSize = 16.sp, color = Color(0xFFCCCCCC)) // ✕
        }
    }
}

@Composable
private fun Chip(text: String, textColor: Color, bgColor: Color) {
    Text(
        text     = text,
        fontSize = 10.sp,
        color    = textColor,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
