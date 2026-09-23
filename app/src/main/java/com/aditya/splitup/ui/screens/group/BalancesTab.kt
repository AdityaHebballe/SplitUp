package com.aditya.splitup.ui.screens.group

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.domain.CurrencyUtils
import com.aditya.splitup.domain.Settlement
import com.aditya.splitup.ui.components.AnimatedAmount
import com.aditya.splitup.ui.components.BalanceCard
import com.aditya.splitup.ui.components.pressScale
import com.aditya.splitup.ui.components.rememberPressInteractionSource
import com.aditya.splitup.ui.theme.AmountStyle
import com.aditya.splitup.ui.theme.AmountStyleLarge
import com.aditya.splitup.ui.theme.HeroCardShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BalancesTab(
    viewModel: GroupViewModel,
    onNavigateToSettleUp: (Long) -> Unit,
    onApplySettlement: (Settlement) -> Unit = {},
    onEditPayment: (Payment) -> Unit = {},
    isTabActive: Boolean = true
) {
    val group by viewModel.group.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val balances by viewModel.memberBalances.collectAsStateWithLifecycle()
    val settlements by viewModel.settlements.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val totalSpent by viewModel.totalSpent.collectAsStateWithLifecycle()
    val ratesStale by viewModel.ratesStale.collectAsStateWithLifecycle()

    val memberMap = remember(members) { members.associateBy { it.id } }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val currentCurrency = group?.defaultCurrency ?: "USD"
    val currentUid = viewModel.currentUid
    val haptic = LocalHapticFeedback.current

    // Animate total spent when user moves to Balances tab after adding/modifying an expense
    var displayedTotal by remember { mutableDoubleStateOf(viewModel.lastSeenBalancesTotal ?: totalSpent) }
    var balancesCycle by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(isTabActive, totalSpent) {
        if (!isTabActive) return@LaunchedEffect

        val previous = viewModel.lastSeenBalancesTotal
        if (previous != null && Math.abs(previous - totalSpent) > 0.001) {
            displayedTotal = previous
            delay(120)
            balancesCycle++
            displayedTotal = totalSpent
            viewModel.lastSeenBalancesTotal = totalSpent
        } else {
            displayedTotal = totalSpent
            viewModel.lastSeenBalancesTotal = totalSpent
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Group Total Spent Card with Settle Up Action
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = HeroCardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Group Total Spent",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedAmount(
                            amount = displayedTotal,
                            currencyCode = currentCurrency,
                            style = AmountStyleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            showStyledSymbol = true,
                            symbolColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            cycle = balancesCycle
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            group?.let { onNavigateToSettleUp(it.id) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Settle Up")
                    }
                }
            }
        }

        if (ratesStale) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Exchange rates may be outdated — couldn't fetch the latest rates. Balances in other currencies may be inaccurate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Section 1: Member Balances Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Member Balances",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (balances.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${balances.size} members",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        if (balances.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = HeroCardShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text("No expenses or balances recorded yet.")
                    }
                }
            }
        } else {
            items(balances, key = { "bal_${it.memberId}" }) { balance ->
                val isSelf = currentUid != null && members.find { it.id == balance.memberId }?.linkedUid == currentUid
                BalanceCard(
                    memberName = balance.memberName,
                    totalPaid = balance.totalPaid,
                    totalOwed = balance.totalOwed,
                    netBalance = balance.netBalance,
                    currencyCode = currentCurrency,
                    isCurrentUser = isSelf,
                    cycle = balancesCycle,
                    modifier = Modifier.animateItem()
                )
            }
        }

        // Section 2: Suggested Settlements Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Suggested Settlements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (settlements.isEmpty()) {
            item {
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(isTabActive) {
                    isVisible = false
                    if (isTabActive) {
                        delay(40)
                        isVisible = true
                    }
                }
                val cardScale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.85f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "settledUpCardScale"
                )
                val pulseTransition = rememberInfiniteTransition(label = "CheckmarkPulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(cardScale)
                        .animateItem(),
                    shape = HeroCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(48.dp)
                                .scale(pulseScale)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "All settled up!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "No outstanding payments needed in this group.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(settlements, key = { "settle_${it.fromMemberId}_${it.toMemberId}" }) { settlement ->
                val interactionSource = rememberPressInteractionSource()
                val isCurrentUserPayer = currentUid != null && members.find { it.id == settlement.fromMemberId }?.linkedUid == currentUid

                // Expressive Directional Flow: gentle drifting arrow indicating money transfer direction
                val infiniteTransition = rememberInfiniteTransition(label = "ArrowFlow_${settlement.fromMemberId}_${settlement.toMemberId}")
                val arrowDrift by infiniteTransition.animateFloat(
                    initialValue = -3f,
                    targetValue = 3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "arrowDriftX"
                )

                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onApplySettlement(settlement)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .pressScale(interactionSource),
                    interactionSource = interactionSource,
                    shape = HeroCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentUserPayer) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    border = if (isCurrentUserPayer) {
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    } else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Flow: Payer -> Arrow -> Receiver (Centered)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = settlement.fromName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = settlement.fromName + if (isCurrentUserPayer) " (You)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "pays",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .offset(x = arrowDrift.dp)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = settlement.toName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = settlement.toName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Integrated Action Button: "Settle $XX.XX"
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onApplySettlement(settlement)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Settle ${CurrencyUtils.formatAmount(settlement.amount, settlement.currency)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ── Past Settlements History ──
        if (payments.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Settlement History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Past payments recorded in this group",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(payments, key = { "past_payment_${it.id}" }) { payment ->
                val fromName = memberMap[payment.fromMemberId]?.name ?: "Member ${payment.fromMemberId}"
                val toName = memberMap[payment.toMemberId]?.name ?: "Member ${payment.toMemberId}"
                val dateStr = dateFormatter.format(Date(payment.createdAt))
                val isCurrentUserPayer = currentUid != null && memberMap[payment.fromMemberId]?.linkedUid == currentUid
                val isCurrentUserReceiver = currentUid != null && memberMap[payment.toMemberId]?.linkedUid == currentUid

                val interactionSource = rememberPressInteractionSource()
                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onEditPayment(payment)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .pressScale(interactionSource),
                    interactionSource = interactionSource,
                    shape = HeroCardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = fromName + if (isCurrentUserPayer) " (You)" else "",
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
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = toName + if (isCurrentUserReceiver) " (You)" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = CurrencyUtils.formatAmount(payment.amount, payment.currency),
                                style = AmountStyle,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
