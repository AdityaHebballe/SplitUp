package com.aditya.splitup.ui.screens.expense

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.SplitGroup
import com.aditya.splitup.ui.components.CategoryChips
import com.aditya.splitup.ui.components.CurrencyPicker
import com.aditya.splitup.ui.components.MemberSelector
import com.aditya.splitup.ui.components.RatioEditor
import com.aditya.splitup.ui.theme.AmountStyleLarge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddExpenseSheet(
    initialGroupId: Long? = null,
    availableGroups: List<SplitGroup> = emptyList(),
    expenseToEdit: Expense? = null,
    onDismiss: () -> Unit,
    viewModel: AddExpenseViewModel
) {
    var selectedGroupId by remember(initialGroupId, availableGroups, expenseToEdit) {
        mutableStateOf(expenseToEdit?.groupId ?: initialGroupId ?: availableGroups.firstOrNull()?.id)
    }

    LaunchedEffect(selectedGroupId) {
        selectedGroupId?.let { viewModel.loadGroup(it) }
    }

    val group by viewModel.group.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()

    var amount by remember(expenseToEdit) { mutableStateOf(expenseToEdit?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var currency by remember(expenseToEdit) { mutableStateOf(expenseToEdit?.currency ?: "USD") }
    var description by remember(expenseToEdit) { mutableStateOf(expenseToEdit?.description ?: "") }
    var paidByMemberId by remember(expenseToEdit) { mutableStateOf<Long?>(expenseToEdit?.paidByMemberId) }
    var category by remember(expenseToEdit) { mutableStateOf<String?>(expenseToEdit?.category) }
    var ratios by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var showCurrencyPicker by remember { mutableStateOf(false) }
    var isRatioExpanded by remember { mutableStateOf(false) }
    var groupDropdownExpanded by remember { mutableStateOf(false) }

    // Load existing splits if editing, or default member ratios if new
    LaunchedEffect(group, members, expenseToEdit) {
        group?.let { grp ->
            if (expenseToEdit == null) {
                currency = grp.defaultCurrency
                val defaultPayer = grp.defaultPayerMemberId ?: members.firstOrNull()?.id
                paidByMemberId = defaultPayer
                ratios = members.associate { it.id to it.defaultRatioPart }
            } else {
                currency = expenseToEdit.currency
                paidByMemberId = expenseToEdit.paidByMemberId
                val existingSplits = viewModel.getSplitsForExpense(expenseToEdit.id)
                if (existingSplits.isNotEmpty()) {
                    ratios = members.associate { m ->
                        val existing = existingSplits.find { it.memberId == m.id }
                        m.id to (existing?.ratioPart ?: m.defaultRatioPart)
                    }
                } else {
                    ratios = members.associate { it.id to it.defaultRatioPart }
                }
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expenseToEdit == null) "Quick Add Expense" else "Edit Expense",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // If multiple groups available and not editing, show group switcher chip
                if (expenseToEdit == null && availableGroups.size > 1) {
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { groupDropdownExpanded = true },
                            label = { Text(group?.name ?: "Select Group") }
                        )
                        DropdownMenu(
                            expanded = groupDropdownExpanded,
                            onDismissRequest = { groupDropdownExpanded = false }
                        ) {
                            availableGroups.forEach { grp ->
                                DropdownMenuItem(
                                    text = { Text(grp.name) },
                                    onClick = {
                                        selectedGroupId = grp.id
                                        groupDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (group == null || members.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else {
                // Large prominent Amount input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .focusRequester(focusRequester),
                    textStyle = AmountStyleLarge,
                    trailingIcon = {
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            ElevatedFilterChip(
                                selected = true,
                                onClick = { showCurrencyPicker = true },
                                label = { Text(currency, fontWeight = FontWeight.Bold) }
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

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true
                )

                Text(
                    "Paid By",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.SemiBold
                )
                MemberSelector(
                    members = members,
                    selectedMemberId = paidByMemberId,
                    onMemberSelected = { paidByMemberId = it }
                )

                Text(
                    "Category (Optional)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontWeight = FontWeight.SemiBold
                )
                CategoryChips(
                    selectedCategory = category,
                    onCategorySelected = { category = it }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Split Ratio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        val ratioDesc = ratios.entries.joinToString(" : ") { it.value.toString() }
                        Text(
                            text = "Current: $ratioDesc",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { isRatioExpanded = !isRatioExpanded }) {
                        Text(if (isRatioExpanded) "Done" else "Customize")
                    }
                }

                AnimatedVisibility(visible = isRatioExpanded) {
                    RatioEditor(
                        members = members,
                        ratios = ratios,
                        onRatioChanged = { id, ratio ->
                            ratios = ratios.toMutableMap().apply { put(id, ratio) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Button(
                    onClick = {
                        val amountVal = amount.toDoubleOrNull()
                        val payerId = paidByMemberId
                        if (amountVal != null && amountVal > 0 && payerId != null && !isSaving) {
                            isSaving = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (expenseToEdit == null) {
                                viewModel.saveExpense(
                                    amount = amountVal,
                                    currency = currency,
                                    description = description,
                                    category = category,
                                    paidByMemberId = payerId,
                                    ratios = ratios,
                                    onSuccess = safeDismiss
                                )
                            } else {
                                viewModel.updateExpense(
                                    expenseId = expenseToEdit.id,
                                    amount = amountVal,
                                    currency = currency,
                                    description = description,
                                    category = category,
                                    paidByMemberId = payerId,
                                    ratios = ratios,
                                    onSuccess = safeDismiss
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = !isSaving && (amount.toDoubleOrNull() ?: 0.0) > 0.0 && paidByMemberId != null
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (expenseToEdit == null) "Adding Expense…" else "Saving Changes…")
                    } else {
                        Text(if (expenseToEdit == null) "Add Expense" else "Save Changes")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus requester may fail if not attached yet
        }
    }
}
