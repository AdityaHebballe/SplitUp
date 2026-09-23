package com.aditya.splitup.ui.screens.payment

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.DeleteOutline
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
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.ui.components.CurrencyPicker
import com.aditya.splitup.ui.components.pressScale
import com.aditya.splitup.ui.components.rememberPressInteractionSource
import com.aditya.splitup.ui.screens.group.GroupViewModel
import com.aditya.splitup.ui.theme.AmountStyleLarge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettleUpSheet(
    viewModel: GroupViewModel,
    paymentToEdit: Payment? = null,
    initialFromMemberId: Long? = null,
    initialToMemberId: Long? = null,
    initialAmount: Double? = null,
    initialCurrency: String? = null,
    onDismiss: () -> Unit
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val allMembers by viewModel.members.collectAsStateWithLifecycle()
    val activeMembers by viewModel.activeMembers.collectAsStateWithLifecycle()
    // Selection (picker + submit validity) only ever offers active members, so a former
    // member can't be picked as payer/recipient for a new settlement. When editing an
    // existing payment that references a since-removed member, fall back to the full
    // member list so their name still resolves correctly instead of showing blank.
    val members = if (paymentToEdit != null) allMembers else activeMembers

    if (members.size < 2) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Need at least 2 members to record payments.")
            }
        }
        return
    }

    val defaultFromId = paymentToEdit?.fromMemberId ?: initialFromMemberId?.takeIf { id -> members.any { it.id == id } } ?: members.first().id
    val defaultToId = paymentToEdit?.toMemberId ?: initialToMemberId?.takeIf { id -> members.any { it.id == id } }
        ?: members.firstOrNull { it.id != defaultFromId }?.id ?: members.getOrNull(1)?.id ?: members.first().id

    var fromMemberId by remember(paymentToEdit, initialFromMemberId) { mutableStateOf(defaultFromId) }
    var toMemberId by remember(paymentToEdit, initialToMemberId) { mutableStateOf(defaultToId) }

    // A member selected here can be removed on another device while this sheet is open
    // (Firestore snapshot listener updates `members` live). Re-derive the selection so we
    // never submit a payment referencing an id that no longer exists in the group.
    LaunchedEffect(members) {
        if (members.none { it.id == fromMemberId }) {
            fromMemberId = members.firstOrNull()?.id ?: fromMemberId
        }
        if (members.none { it.id == toMemberId }) {
            toMemberId = members.firstOrNull { it.id != fromMemberId }?.id ?: fromMemberId
        }
    }
    val effectiveAmount = paymentToEdit?.amount ?: initialAmount
    var amount by remember(paymentToEdit, initialAmount) {
        mutableStateOf(
            if (effectiveAmount != null && effectiveAmount > 0.0) {
                String.format(java.util.Locale.US, "%.2f", effectiveAmount)
            } else ""
        )
    }
    var currency by remember(paymentToEdit, initialCurrency, group?.defaultCurrency) {
        mutableStateOf(paymentToEdit?.currency ?: initialCurrency ?: group?.defaultCurrency ?: "USD")
    }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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

    var isSaving by remember { mutableStateOf(false) }

    val safeDismiss: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        scope.launch {
            try {
                sheetState.hide()
            } catch (_: Exception) {}
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        BackHandler(enabled = true) {
            if (isImeVisible) {
                focusManager.clearFocus()
                keyboardController?.hide()
            } else {
                safeDismiss()
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
                text = if (paymentToEdit != null) "Edit Settlement" else "Record Settle-Up",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (paymentToEdit != null) "Update or revert this settled payment." else "Track money sent from one person to another to reduce owed balances.",
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
                            com.aditya.splitup.domain.CurrencyUtils.currencies.forEach { curr ->
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
                    if (parsed != null && parsed > 0 && fromMemberId != toMemberId && !isSaving) {
                        isSaving = true
                        if (paymentToEdit != null) {
                            viewModel.updatePayment(
                                paymentToEdit.copy(
                                    fromMemberId = fromMemberId,
                                    toMemberId = toMemberId,
                                    amount = parsed,
                                    currency = currency
                                ),
                                onSuccess = safeDismiss
                            )
                        } else {
                            viewModel.recordPayment(
                                fromMemberId = fromMemberId,
                                toMemberId = toMemberId,
                                amount = parsed,
                                currency = currency,
                                onSuccess = safeDismiss
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isSaving && (amount.toDoubleOrNull() ?: 0.0) > 0.0 && fromMemberId != toMemberId &&
                    members.any { it.id == fromMemberId } && members.any { it.id == toMemberId }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (paymentToEdit != null) "Updating…" else "Recording…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(Icons.Filled.Payments, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (paymentToEdit != null) "Update Settlement" else "Record Settle Up",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (paymentToEdit != null) {
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Revert Settlement", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showDeleteConfirmDialog && paymentToEdit != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Revert Settlement?") },
                text = {
                    val fromName = members.find { it.id == paymentToEdit.fromMemberId }?.name ?: "Payer"
                    val toName = members.find { it.id == paymentToEdit.toMemberId }?.name ?: "Receiver"
                    Text("This will delete this settlement record and restore the balance between $fromName and $toName.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deletePayment(paymentToEdit)
                            safeDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Revert", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
