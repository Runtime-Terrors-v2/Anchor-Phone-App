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

@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onBack:    () -> Unit
) {
    val contacts  by viewModel.contacts.collectAsState()
    var showForm  by remember { mutableStateOf(false) }
    var newName   by remember { mutableStateOf("") }
    var newPhone  by remember { mutableStateOf("") }
    var newOpenID by remember { mutableStateOf("") }
    var errorMsg  by remember { mutableStateOf("") }

    fun addContact() {
        if (newName.isBlank()) { errorMsg = "Please enter a name"; return }
        if (newPhone.isBlank() && newOpenID.isBlank()) {
            errorMsg = "Enter a phone number or app user ID"; return
        }
        viewModel.addContact(newName, newPhone, newOpenID)
        newName = ""; newPhone = ""; newOpenID = ""; showForm = false; errorMsg = ""
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
                .background(White)
                .border(width = 0.5.dp, color = Color(0xFFE5E7EB))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = onBack) {
                    Text("←", fontSize = 22.sp, color = TextPrimary)
                }
                Text(
                    text       = "Priority Contacts",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
            }
            Button(
                onClick  = { showForm = !showForm; errorMsg = "" },
                modifier = Modifier.height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = DeepBlue)
            ) {
                Text("+ Add", fontSize = 15.sp)
            }
        }

        // --- Info banner ---
        Row(
            modifier              = Modifier
                .fillMaxWidth(0.9f)
                .padding(top = 16.dp)
                .background(BlueLight, RoundedCornerShape(12.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment     = Alignment.Top
        ) {
            Text(
                text       = "ℹ",
                fontSize   = 16.sp,
                color      = DeepBlue,
                fontWeight = FontWeight.Bold
            )
            Text(
                text     = "These contacts will receive an SMS alert the moment your watch goes offline.",
                fontSize = 14.sp,
                color    = Color(0xFF1565C0),
                modifier = Modifier.weight(1f),
                lineHeight = 21.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding      = PaddingValues(bottom = 48.dp)
        ) {
            // --- Add-contact form ---
            if (showForm) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth(0.9f)
                            .background(White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text       = "Add a contact",
                            fontSize   = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = TextPrimary
                        )

                        FormField(
                            label       = "Full name *",
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
                            Text(
                                text     = errorMsg,
                                fontSize = 14.sp,
                                color    = AlertRed
                            )
                        }

                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick  = {
                                    showForm = false; errorMsg = ""
                                    newName = ""; newPhone = ""; newOpenID = ""
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", fontSize = 15.sp, color = TextSecond)
                            }
                            Button(
                                onClick  = { addContact() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape    = RoundedCornerShape(12.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = DeepBlue)
                            ) {
                                Text("Save", fontSize = 15.sp)
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
                        modifier            = Modifier.padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("👥", fontSize = 48.sp)
                        Text(
                            text       = "No contacts added yet",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color      = TextSecond
                        )
                        Text(
                            text     = "Add people who should be\nalerted if your watch disconnects.",
                            fontSize = 15.sp,
                            color    = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick  = { showForm = true },
                            modifier = Modifier.height(52.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = DeepBlue)
                        ) {
                            Text("+ Add first contact", fontSize = 15.sp)
                        }
                    }
                }
            }

            // --- Contact list ---
            items(contacts, key = { it.id }) { contact ->
                Spacer(modifier = Modifier.height(8.dp))
                ContactRow(
                    contact  = contact,
                    onRemove = { viewModel.removeContact(contact.id) }
                )
            }
        }
    }
}

@Composable
private fun FormField(
    label:       String,
    value:       String,
    placeholder: String,
    keyboard:    KeyboardType = KeyboardType.Text,
    onValue:     (String) -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextSecond)
        OutlinedTextField(
            value           = value,
            onValueChange   = onValue,
            placeholder     = { Text(placeholder, fontSize = 15.sp, color = TextMuted) },
            modifier        = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape           = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            singleLine      = true,
            textStyle       = LocalTextStyle.current.copy(fontSize = 15.sp),
            colors          = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFE5E7EB),
                focusedBorderColor   = DeepBlue
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
            .background(White, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Avatar
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(52.dp)
                .background(PurpleLight, CircleShape)
        ) {
            Text(
                text       = initials(contact.name),
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color(0xFF5B21B6)
            )
        }

        // Info
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text       = contact.name,
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = TextPrimary
            )
            if (contact.phoneNumber.isNotBlank()) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Tag("SMS", Color(0xFF065F46), Color(0xFFD1FAE5))
                    Text(contact.phoneNumber, fontSize = 14.sp, color = TextSecond)
                }
            }
            if (contact.openID.isNotBlank()) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Tag("APP", Color(0xFF5B21B6), PurpleLight)
                    Text(
                        text     = contact.openID.take(16) + "…",
                        fontSize = 14.sp,
                        color    = TextSecond
                    )
                }
            }
        }

        // Remove
        TextButton(
            onClick  = onRemove,
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text("✕", fontSize = 18.sp, color = Color(0xFFD1D5DB))
        }
    }
}

@Composable
private fun Tag(text: String, textColor: Color, bgColor: Color) {
    Text(
        text     = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color    = textColor,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}
