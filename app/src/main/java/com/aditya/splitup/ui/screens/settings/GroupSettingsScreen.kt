package com.aditya.splitup.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditya.splitup.ui.components.InviteSheet
import com.aditya.splitup.ui.components.pressScale
import com.aditya.splitup.ui.components.rememberPressInteractionSource
import com.aditya.splitup.ui.theme.CardShape
import com.aditya.splitup.ui.components.CurrencyPicker
import com.aditya.splitup.ui.components.RatioEditor
import com.aditya.splitup.data.model.Member

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupSettingsScreen(
    groupId: Long,
    viewModel: GroupSettingsViewModel,
    onNavigateBack: () -> Unit,
    onGroupDeleted: () -> Unit = onNavigateBack
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val inviteCode by viewModel.inviteCode.collectAsStateWithLifecycle()
    val isGeneratingInvite by viewModel.isGeneratingInvite.collectAsStateWithLifecycle()
    val inviteErrorMessage by viewModel.inviteErrorMessage.collectAsStateWithLifecycle()
    var newMemberName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSelfLeaveDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<Member?>(null) }
    var showInviteSheet by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(groupId) {
        viewModel.loadGroup(groupId)
    }

    if (group == null) {
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = group!!.name,
                    onValueChange = { viewModel.updateGroupName(it) },
                    label = { Text("Group Name") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text("Default Currency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                CurrencyPicker(
                    currentCurrency = group!!.defaultCurrency,
                    onCurrencySelected = { viewModel.updateDefaultCurrency(it) }
                )
            }

            item {
                Text("Split Ratios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                RatioEditor(
                    members = members,
                    ratios = members.associate { it.id to it.defaultRatioPart },
                    onRatioChanged = { memberId, newRatio ->
                        val member = members.find { it.id == memberId }
                        if (member != null) {
                            viewModel.updateMemberRatio(member, newRatio)
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Invite Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showInviteSheet = true
                        viewModel.generateInvite()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generate Invite Code")
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Manage Members", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newMemberName,
                    onValueChange = { newMemberName = it },
                    placeholder = { Text("Add new member name") },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = newMemberName.isNotBlank(),
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            FilledIconButton(
                                onClick = {
                                    if (newMemberName.isNotBlank()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.addMember(newMemberName.trim())
                                        newMemberName = ""
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add Member",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newMemberName.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.addMember(newMemberName.trim())
                                newMemberName = ""
                            }
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(members) { member ->
                val currentUid = viewModel.currentUid
                val isOwner = viewModel.isOwner
                val isSelf = member.linkedUid != null && member.linkedUid == currentUid

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = member.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                if (isSelf) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "You",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (isOwner || isSelf) {
                            FilledTonalIconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isSelf) {
                                        showSelfLeaveDialog = true
                                    } else {
                                        memberToRemove = member
                                    }
                                },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = if (isSelf) Icons.AutoMirrored.Outlined.ExitToApp else Icons.Outlined.DeleteOutline,
                                    contentDescription = if (isSelf) "Leave Group" else "Remove"
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text("Default Payer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    members.forEach { member ->
                        val isSelected = member.id == group!!.defaultPayerMemberId
                        val interactionSource = rememberPressInteractionSource()
                        ElevatedCard(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.updateDefaultPayer(member.id)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScale(interactionSource),
                            interactionSource = interactionSource,
                            shape = CardShape,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = member.name.take(1).uppercase(),
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = member.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.updateDefaultPayer(member.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                val isOwner = viewModel.isOwner

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isOwner) Icons.Outlined.DeleteOutline else Icons.AutoMirrored.Outlined.ExitToApp,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isOwner) "Delete Group" else "Leave Group", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDeleteDialog) {
        val isOwner = viewModel.isOwner
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = if (isOwner) Icons.Outlined.DeleteOutline else Icons.AutoMirrored.Outlined.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (isOwner) "Delete Group" else "Leave Group") },
            text = {
                Text(
                    if (isOwner)
                        "Are you sure you want to delete this group? This will permanently delete the group, expenses, payments, and active invites for all members. This action cannot be undone."
                    else
                        "Are you sure you want to leave this group? The group will be removed from your device, but remains available for other members."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(onSuccess = onGroupDeleted)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (isOwner) "Delete" else "Leave", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSelfLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showSelfLeaveDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Leave Group?") },
            text = {
                Text("Deleting your member profile will remove you from this group. Your expense history will be preserved for the other members. Are you sure you want to leave?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSelfLeaveDialog = false
                        viewModel.leaveGroup(onSuccess = onGroupDeleted)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Leave Group", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelfLeaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (memberToRemove != null) {
        val target = memberToRemove!!
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Remove Member?") },
            text = {
                Text("Are you sure you want to remove ${target.name} from the group? Their existing expenses and splits will be preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeMember(target)
                        memberToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showInviteSheet) {
        InviteSheet(
            inviteCode = inviteCode,
            isGenerating = isGeneratingInvite,
            errorMessage = inviteErrorMessage,
            onGenerate = { viewModel.generateInvite() },
            onDismiss = {
                showInviteSheet = false
                viewModel.clearInviteCode()
            }
        )
    }
}
