package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensetracker.domain.CurrencyUtils

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BalanceCard(
    memberName: String,
    totalPaid: Double,
    netBalance: Double,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val color = when {
        netBalance > 0.009 -> Color(0xFF4CAF50) // Green
        netBalance < -0.009 -> Color(0xFFF44336) // Red
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val netText = when {
        netBalance > 0.009 -> buildAnnotatedString {
            append("Gets back ")
            append(CurrencyUtils.formatAmountStyled(kotlin.math.abs(netBalance), currencyCode, symbolColor = color.copy(alpha = 0.7f)))
        }
        netBalance < -0.009 -> buildAnnotatedString {
            append("Owes ")
            append(CurrencyUtils.formatAmountStyled(kotlin.math.abs(netBalance), currencyCode, symbolColor = color.copy(alpha = 0.7f)))
        }
        else -> buildAnnotatedString { append("Settled up") }
    }

    val containerBg = when {
        netBalance > 0.009 -> Color(0xFF4CAF50).copy(alpha = 0.12f)
        netBalance < -0.009 -> Color(0xFFF44336).copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = memberName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Column {
                    Text(
                        text = memberName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildAnnotatedString {
                            append("Paid ")
                            append(CurrencyUtils.formatAmountStyled(totalPaid, currencyCode))
                            append(" total")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = containerBg,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = netText,
                    style = MaterialTheme.typography.labelLarge,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
