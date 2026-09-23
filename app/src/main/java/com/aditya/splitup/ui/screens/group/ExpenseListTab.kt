package com.aditya.splitup.ui.screens.group

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.domain.CurrencyUtils
import com.aditya.splitup.ui.components.AnimatedAmount
import com.aditya.splitup.ui.components.expenseCategories
import com.aditya.splitup.ui.components.pressScale
import com.aditya.splitup.ui.components.rememberPressInteractionSource
import com.aditya.splitup.ui.theme.AmountStyle
import com.aditya.splitup.ui.theme.CardShape
import com.aditya.splitup.ui.theme.HeroCardShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class GroupFeedItem(val timestamp: Long, val key: String) {
    data class ExpenseEntry(val expense: Expense) : GroupFeedItem(expense.createdAt, "exp_${expense.id}")
    data class PaymentEntry(val payment: Payment) : GroupFeedItem(payment.createdAt, "pay_${payment.id}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListTab(
    viewModel: GroupViewModel,
    onEditExpense: (Expense) -> Unit,
    onEditPayment: (Payment) -> Unit = {},
    isTabActive: Boolean = true
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val memberMap = remember(members) { members.associateBy { it.id } }
    val categoryIconMap = remember { expenseCategories.associate { it.name to it.icon } }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val haptic = LocalHapticFeedback.current
    val currentUid = viewModel.currentUid

    val feedItems = remember(expenses, payments) {
        (expenses.map { GroupFeedItem.ExpenseEntry(it) } +
         payments.map { GroupFeedItem.PaymentEntry(it) })
            .sortedByDescending { it.timestamp }
    }

    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var paymentToDelete by remember { mutableStateOf<Payment?>(null) }


    if (feedItems.isEmpty()) {
        var isVisible by remember { mutableStateOf(false) }
        LaunchedEffect(isTabActive) {
            isVisible = false
            if (isTabActive) {
                kotlinx.coroutines.delay(40)
                isVisible = true
            }
        }
        val emptyScale by animateFloatAsState(
            targetValue = if (isVisible) 1f else 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "emptyStateScale"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.scale(emptyScale)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No expenses or settlements yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap + to quickly log the first one!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Hint pill indicating swipe left to delete and tap to edit
            item {
                val hintTransition = rememberInfiniteTransition(label = "SwipeHintNudge")
                val arrowNudge by hintTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = -3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "arrowNudgeX"
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .offset(x = arrowNudge.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Slide left to delete",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Tap to edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            items(feedItems, key = { it.key }) { feedItem ->
                when (feedItem) {
                    is GroupFeedItem.ExpenseEntry -> {
                        val expense = feedItem.expense
                        val payer = memberMap[expense.paidByMemberId]
                        val payerSuffix = if (payer?.isRemoved == true) " (former member)" else ""
                        val payerName = (payer?.name ?: "Member ${expense.paidByMemberId}") + payerSuffix
                        val catIcon = categoryIconMap[expense.category] ?: "💰"
                        val dateStr = dateFormatter.format(Date(expense.createdAt))
                        val interactionSource = rememberPressInteractionSource()
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val catIconScale by animateFloatAsState(
                            targetValue = if (isPressed) 1.15f else 1f,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            label = "catIconScale"
                        )

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    expenseToDelete = expense
                                    false // Rebound smoothly while asking for confirmation
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                                val bgColor by animateColorAsState(
                                    targetValue = if (isSwiping) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    label = "swipeBgColor"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSwiping) 1.15f else 0.85f,
                                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    label = "swipeIconScale"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CardShape)
                                        .background(bgColor)
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.scale(iconScale)
                                    ) {
                                        Text(
                                            text = "Delete",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            ElevatedCard(
                                onClick = { onEditExpense(expense) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressScale(interactionSource),
                                interactionSource = interactionSource,
                                shape = CardShape,
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .scale(catIconScale)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(text = catIcon, style = MaterialTheme.typography.titleLarge)
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = expense.description.ifBlank { expense.category ?: "Expense" },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Paid by $payerName",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }

                                    AnimatedAmount(
                                        amount = expense.amount,
                                        currencyCode = expense.currency,
                                        style = AmountStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        showStyledSymbol = true,
                                        symbolColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    is GroupFeedItem.PaymentEntry -> {
                        val payment = feedItem.payment
                        val fromMember = memberMap[payment.fromMemberId]
                        val toMember = memberMap[payment.toMemberId]
                        val isCurrentUserPayer = currentUid != null && fromMember?.linkedUid == currentUid
                        val isCurrentUserReceiver = currentUid != null && toMember?.linkedUid == currentUid
                        val fromSuffix = if (fromMember?.isRemoved == true) " (former)" else if (isCurrentUserPayer) " (You)" else ""
                        val toSuffix = if (toMember?.isRemoved == true) " (former)" else if (isCurrentUserReceiver) " (You)" else ""
                        val fromName = (fromMember?.name ?: "Member ${payment.fromMemberId}") + fromSuffix
                        val toName = (toMember?.name ?: "Member ${payment.toMemberId}") + toSuffix
                        val dateStr = dateFormatter.format(Date(payment.createdAt))
                        val interactionSource = rememberPressInteractionSource()

                        // Directional arrow drift in feed
                        val payArrowTransition = rememberInfiniteTransition(label = "PayFeedArrow_${payment.id}")
                        val payArrowDrift by payArrowTransition.animateFloat(
                            initialValue = -2f,
                            targetValue = 2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "payArrowDriftX"
                        )

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    paymentToDelete = payment
                                    false
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = {
                                val isSwiping = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                                val bgColor by animateColorAsState(
                                    targetValue = if (isSwiping) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    label = "swipePayBgColor"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (isSwiping) 1.15f else 0.85f,
                                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    label = "swipePayIconScale"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CardShape)
                                        .background(bgColor)
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.scale(iconScale)
                                    ) {
                                        Text(
                                            text = "Revert",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Revert",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            ElevatedCard(
                                onClick = { onEditPayment(payment) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressScale(interactionSource),
                                interactionSource = interactionSource,
                                shape = CardShape,
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Payments,
                                                    contentDescription = "Settlement",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = fromName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                    contentDescription = "paid",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .offset(x = payArrowDrift.dp)
                                                )
                                                Text(
                                                    text = toName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Settlement · $dateStr",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    ) {
                                        AnimatedAmount(
                                            amount = payment.amount,
                                            currencyCode = payment.currency,
                                            style = AmountStyle,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            shape = HeroCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Delete Expense?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${expenseToDelete?.description?.ifBlank { expenseToDelete?.category } ?: "this expense"}\"? All balances will be recalculated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        expenseToDelete?.let { viewModel.deleteExpense(it) }
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { expenseToDelete = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (paymentToDelete != null) {
        val payment = paymentToDelete!!
        val fromName = memberMap[payment.fromMemberId]?.name ?: "Payer"
        val toName = memberMap[payment.toMemberId]?.name ?: "Receiver"
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            shape = HeroCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Revert Settlement?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to revert the payment of ${CurrencyUtils.formatAmount(payment.amount, payment.currency)}? This will restore the debt between $fromName and $toName.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePayment(payment)
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Revert", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { paymentToDelete = null },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

