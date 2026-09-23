package com.aditya.splitup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aditya.splitup.domain.CurrencyUtils
import com.aditya.splitup.ui.theme.AmountStyle
import com.aditya.splitup.ui.theme.HeroCardShape
import com.aditya.splitup.ui.theme.LocalBalanceColors
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BalanceCard(
    memberName: String,
    totalPaid: Double,
    totalOwed: Double,
    netBalance: Double,
    currencyCode: String,
    isCurrentUser: Boolean = false,
    modifier: Modifier = Modifier
) {
    val balanceColors = LocalBalanceColors.current
    val isPositive = netBalance > 0.009
    val isNegative = netBalance < -0.009
    val isSettled = !isPositive && !isNegative

    val statusColor = when {
        isPositive -> balanceColors.positive
        isNegative -> balanceColors.negative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusBg = when {
        isPositive -> balanceColors.positive.copy(alpha = 0.12f)
        isNegative -> balanceColors.negative.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val cardBorder = if (isCurrentUser) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    } else null

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = HeroCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Avatar + Name + (You) Badge + Contextual Breakdown (Paid vs Share)
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = memberName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = memberName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isCurrentUser) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "You",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Contextual financial breakdown
                    Text(
                        text = "Paid ${CurrencyUtils.formatAmount(totalPaid, currencyCode)} • Share ${CurrencyUtils.formatAmount(totalOwed, currencyCode)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right: Expressive Balance Pillar (Amount + Directional Pill)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val amountPrefix = when {
                    isPositive -> "+"
                    isNegative -> "-"
                    else -> ""
                }
                Text(
                    text = "$amountPrefix${CurrencyUtils.formatAmount(abs(netBalance), currencyCode)}",
                    style = AmountStyle,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val icon = when {
                            isPositive -> Icons.AutoMirrored.Filled.TrendingUp
                            isNegative -> Icons.AutoMirrored.Filled.TrendingDown
                            else -> Icons.Default.CheckCircle
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(12.dp)
                        )
                        val label = when {
                            isPositive -> "gets back"
                            isNegative -> "owes"
                            else -> "settled"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}
