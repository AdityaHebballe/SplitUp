package com.aditya.splitup.ui.screens.create

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.filled.Add
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
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.ui.components.CurrencyPicker
import com.aditya.splitup.ui.components.RatioEditor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreateGroupScreen(
    viewModel: CreateGroupViewModel,
    onNavigateBack: () -> Unit,
    onGroupCreated: (Long) -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    
    val groupName by viewModel.groupName.collectAsStateWithLifecycle()
    val memberNames by viewModel.memberNames.collectAsStateWithLifecycle()
    val ratios by viewModel.ratios.collectAsStateWithLifecycle()
    val defaultCurrency by viewModel.defaultCurrency.collectAsStateWithLifecycle()
    val defaultPayerIndex by viewModel.defaultPayerIndex.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) currentStep--
                        else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LinearProgressIndicator(
                progress = { currentStep / 5f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(targetState = currentStep, label = "StepTransition") { step ->
                when (step) {
                    1 -> Step1GroupName(
                        groupName = groupName,
                        onNameChange = { viewModel.updateGroupName(it) },
                        onNext = { if (groupName.isNotBlank()) currentStep++ }
                    )
                    2 -> Step2Members(
                        members = memberNames,
                        onAddMember = { viewModel.addMember(it) },
                        onRemoveMember = { viewModel.removeMember(it) },
                        onNext = { if (memberNames.size >= 2) currentStep++ }
                    )
                    3 -> Step3Ratios(
                        memberNames = memberNames,
                        ratios = ratios,
                        onRatioChange = { name, ratio -> viewModel.updateRatio(name, ratio) },
                        onNext = { currentStep++ }
                    )
                    4 -> Step4Currency(
                        currency = defaultCurrency,
                        onCurrencyChange = { viewModel.setDefaultCurrency(it) },
                        onNext = { currentStep++ }
                    )
                    5 -> Step5DefaultPayer(
                        members = memberNames,
                        defaultPayerIndex = defaultPayerIndex,
                        onPayerIndexChange = { viewModel.setDefaultPayerIndex(it) },
                        onCreate = { viewModel.createGroup(onGroupCreated) }
                    )
                }
            }
        }
    }
}

@Composable
fun Step1GroupName(groupName: String, onNameChange: (String) -> Unit, onNext: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("What's the group name?", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = groupName,
            onValueChange = onNameChange,
            label = { Text("Group Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onNext,
            enabled = groupName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next")
        }
    }
}

@Composable
fun Step2Members(
    members: List<String>,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onNext: () -> Unit
) {
    var newMemberName by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Who's in the group?", style = MaterialTheme.typography.headlineMedium)
        Text("Add at least 2 members", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = newMemberName,
            onValueChange = { newMemberName = it },
            placeholder = { Text("Add member name") },
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
                                onAddMember(newMemberName.trim())
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
                        onAddMember(newMemberName.trim())
                        newMemberName = ""
                    }
                }
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(members) { name ->
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
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRemoveMember(name)
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Remove"
                            )
                        }
                    }
                }
            }
        }
        
        Button(
            onClick = onNext,
            enabled = members.size >= 2,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next")
        }
    }
}

@Composable
fun Step3Ratios(
    memberNames: List<String>,
    ratios: Map<String, Int>,
    onRatioChange: (String, Int) -> Unit,
    onNext: () -> Unit
) {
    // Convert to dummy Member objects for RatioEditor
    val dummyMembers = memberNames.mapIndexed { index, name ->
        Member(id = index.toLong(), groupId = 0L, name = name, defaultRatioPart = ratios[name] ?: 1)
    }
    val dummyRatios = dummyMembers.associate { it.id to (ratios[it.name] ?: 1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Set default split ratios", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        RatioEditor(
            members = dummyMembers,
            ratios = dummyRatios,
            onRatioChanged = { id, ratio ->
                val name = dummyMembers.find { it.id == id }?.name
                if (name != null) {
                    onRatioChange(name, ratio)
                }
            },
            modifier = Modifier.weight(1f)
        )
        
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun Step4Currency(
    currency: String,
    onCurrencyChange: (String) -> Unit,
    onNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Choose group currency", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        CurrencyPicker(
            currentCurrency = currency,
            onCurrencySelected = onCurrencyChange
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Next")
        }
    }
}

@Composable
fun Step5DefaultPayer(
    members: List<String>,
    defaultPayerIndex: Int,
    onPayerIndexChange: (Int) -> Unit,
    onCreate: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Who usually pays?", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(members.size) { index ->
                val isSelected = defaultPayerIndex == index
                val name = members[index]
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPayerIndexChange(index)
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
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
                                        text = name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPayerIndexChange(index)
                            }
                        )
                    }
                }
            }
        }
        
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCreate()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Group")
        }
    }
}
