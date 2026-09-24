package com.aditya.splitup.ui.screens.join

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditya.splitup.ui.theme.HeroCardShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JoinGroupScreen(
    viewModel: JoinGroupViewModel,
    initialCode: String? = null,
    onNavigateBack: () -> Unit,
    onGroupJoined: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    val canStepBack = state is JoinState.Preview || state is JoinState.AlreadyMember || state is JoinState.Rejoin
    BackHandler(enabled = canStepBack) {
        viewModel.resetToEnterCode()
    }

    LaunchedEffect(initialCode) {
        if (!initialCode.isNullOrBlank()) {
            val sanitized = initialCode.trim().removePrefix("/").uppercase().take(6)
            if (sanitized.length == 6) {
                viewModel.lookupCode(sanitized)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join a Group") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state is JoinState.Preview || state is JoinState.AlreadyMember || state is JoinState.Rejoin) viewModel.resetToEnterCode()
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "JoinStateTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) { currentState ->
            when (currentState) {
                is JoinState.EnterCode, is JoinState.Error -> {
                    EnterCodeStep(
                        initialCode = initialCode ?: "",
                        error = (currentState as? JoinState.Error)?.message,
                        onLookup = { code ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.lookupCode(code)
                        }
                    )
                }

                JoinState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Looking up invite…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                is JoinState.AlreadyMember -> {
                    AlreadyMemberStep(
                        groupName = currentState.groupName,
                        onOpenGroup = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onGroupJoined(currentState.localGroupId)
                        }
                    )
                }

                is JoinState.Rejoin -> {
                    RejoinStep(
                        groupName = currentState.invite.groupName,
                        memberName = currentState.memberName,
                        onRejoin = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.rejoinGroup(
                                memberFsId = currentState.memberFsId,
                                memberName = currentState.memberName,
                                onSuccess = onGroupJoined
                            )
                        },
                        onJoinAsNew = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.enterAsNewMember()
                        }
                    )
                }

                is JoinState.Preview -> {
                    EnterNameStep(
                        groupName = currentState.invite.groupName,
                        memberCount = currentState.memberCount,
                        claimableMembers = currentState.claimableMembers,
                        onJoin = { name ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.joinGroup(name, onGroupJoined)
                        }
                    )
                }

                JoinState.Success -> {
                    // Navigation handled by ViewModel callback — show brief success state
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("✓ Joined!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RejoinStep(
    groupName: String,
    memberName: String,
    onRejoin: () -> Unit,
    onJoinAsNew: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Surface(
            shape = HeroCardShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            groupName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Welcome back! You were previously in this group as $memberName.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onRejoin,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                "Rejoin as $memberName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onJoinAsNew,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                "Join as a new member instead",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AlreadyMemberStep(
    groupName: String,
    onOpenGroup: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Surface(
            shape = HeroCardShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            groupName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You are already a member of this group!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onOpenGroup,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                "Open Group",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EnterCodeStep(
    initialCode: String = "",
    error: String?,
    onLookup: (String) -> Unit
) {
    var code by remember(initialCode) {
        mutableStateOf(initialCode.trim().removePrefix("/").uppercase().take(6))
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Surface(
            shape = HeroCardShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Key,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Enter invite code",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask the group creator to share their invite code with you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(6) },
            label = { Text("6-character code") },
            placeholder = { Text("e.g. AB3X7K") },
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { if (code.length == 6) onLookup(code) }),
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.headlineSmall.copy(
                letterSpacing = 6.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onLookup(code) },
            enabled = code.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Look Up Group")
        }
    }
}

@Composable
private fun EnterNameStep(
    groupName: String,
    memberCount: Int,
    claimableMembers: List<String> = emptyList(),
    onJoin: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Surface(
            shape = HeroCardShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            groupName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "$memberCount member${if (memberCount != 1) "s" else ""} already in this group",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Text(
            "What's your name?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter your name to join, or tap a previous member profile below if you were in this group before.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") },
            leadingIcon = {
                Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Go
            ),
            keyboardActions = KeyboardActions(onGo = { if (name.isNotBlank()) onJoin(name.trim()) }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // Show claimable members chips or matching suggestion
        if (claimableMembers.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            val matching = if (name.isNotBlank()) {
                claimableMembers.filter { it.contains(name.trim(), ignoreCase = true) }
            } else claimableMembers

            if (matching.isNotEmpty()) {
                Text(
                    if (name.isBlank()) "Were you previously in this group?" else "Claim previous profile:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matching.forEach { claimable ->
                        val isSelected = name.trim().equals(claimable, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                name = claimable
                            },
                            label = { Text(claimable) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { onJoin(name.trim()) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            val isClaiming = claimableMembers.any { it.equals(name.trim(), ignoreCase = true) }
            Text(
                if (isClaiming) "Rejoin as \"${name.trim()}\""
                else "Join as \"${name.ifBlank { "..." }}\""
            )
        }
    }
}
