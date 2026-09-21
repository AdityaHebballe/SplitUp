package com.example.expensetracker.ui.screens.payment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensetracker.ui.components.CurrencyPicker
import com.example.expensetracker.ui.components.pressScale
import com.example.expensetracker.ui.components.rememberPressInteractionSource
import com.example.expensetracker.ui.screens.group.GroupViewModel
import com.example.expensetracker.ui.theme.AmountStyleLarge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettleUpSheet(
    viewModel: GroupViewModel,
    onDismiss: () -> Unit
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()

    if (members.size < 2) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Need at least 2 members to record payments.")
            }
        }
        return
    }

    var fromMemberId by remember { mutableStateOf(members.first().id) }
    var toMemberId by remember { mutableStateOf(members[1].id) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(group?.defaultCurrency ?: "USD") }
    var showCurrencyPicker by remember { mutableStateOf(false) }

    var fromDropdownExpanded by remember { mutableStateOf(false) }
    var toDropdownExpanded by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val isImeVisible by remember {
        derivedStateOf { imeInsets.getBottom(density) > 0 }
    }

    val dismissWithAnimation: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        scope.launch {
            try {
                sheetState.hide()
            } catch (_: Exception) {}
        }.invokeOnCompletion {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isImeVisible) {
                focusManager.clearFocus()
                keyboardController?.hide()
            } else {
                dismissWithAnimation()
            }
        },
        sheetState = sheetState,
        properties = ModalBottomSheetDefaults.properties(
            shouldDismissOnBackPress = false
        )
    ) {
        BackHandler(enabled = true) {
            if (isImeVisible) {
                focusManager.clearFocus()
                keyboardController?.hide()
            } else {
                dismissWithAnimation()
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Record Settle-Up",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Track money sent from one person to another to reduce owed balances.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Payer (From) and Receiver (To) selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // From Member
                val fromMember = members.find { it.id == fromMemberId }
                val fromInteractionSource = rememberPressInteractionSource()
                Box(modifier = Modifier.weight(1f)) {
                    ElevatedCard(
                        onClick = { fromDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(fromInteractionSource),
                        interactionSource = fromInteractionSource,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Who Paid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (fromMember?.name ?: "").take(1).uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = fromMember?.name ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = fromDropdownExpanded,
                        onDismissRequest = { fromDropdownExpanded = false }
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    fromMemberId = m.id
                                    if (toMemberId == m.id) {
                                        toMemberId = members.firstOrNull { it.id != m.id }?.id ?: m.id
                                    }
                                    fromDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                // To Member
                val toMember = members.find { it.id == toMemberId }
                val toInteractionSource = rememberPressInteractionSource()
                Box(modifier = Modifier.weight(1f)) {
                    ElevatedCard(
                        onClick = { toDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(toInteractionSource),
                        interactionSource = toInteractionSource,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Who Received",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (toMember?.name ?: "").take(1).uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                                Text(
                                    text = toMember?.name ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    DropdownMenu(
                        expanded = toDropdownExpanded,
                        onDismissRequest = { toDropdownExpanded = false }
                    ) {
                        members.filter { it.id != fromMemberId }.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    toMemberId = m.id
                                    toDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Amount with Currency picker
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount Paid") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                textStyle = AmountStyleLarge,
                trailingIcon = {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = currency,
                            modifier = Modifier
                                .clickable { showCurrencyPicker = true }
                                .padding(8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        DropdownMenu(
                            expanded = showCurrencyPicker,
                            onDismissRequest = { showCurrencyPicker = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            com.example.expensetracker.domain.CurrencyUtils.currencies.forEach { curr ->
                                val isSelected = curr.code.equals(currency, ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${curr.code} (${curr.symbol}) — ${curr.name}",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        currency = curr.code
                                        showCurrencyPicker = false
                                    }
                                )
                            }
                        }
                    }
                }
            )

            Button(
                onClick = {
                    val parsed = amount.toDoubleOrNull()
                    if (parsed != null && parsed > 0 && fromMemberId != toMemberId) {
                        viewModel.recordPayment(
                            fromMemberId = fromMemberId,
                            toMemberId = toMemberId,
                            amount = parsed,
                            currency = currency,
                            onSuccess = dismissWithAnimation
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = (amount.toDoubleOrNull() ?: 0.0) > 0.0 && fromMemberId != toMemberId
            ) {
                Icon(Icons.Filled.Payments, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Record Settle Up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
