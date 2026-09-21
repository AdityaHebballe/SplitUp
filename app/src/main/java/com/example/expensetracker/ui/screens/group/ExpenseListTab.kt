package com.example.expensetracker.ui.screens.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensetracker.data.model.Expense
import com.example.expensetracker.domain.CurrencyUtils
import com.example.expensetracker.ui.components.expenseCategories
import com.example.expensetracker.ui.components.pressScale
import com.example.expensetracker.ui.components.rememberPressInteractionSource
import com.example.expensetracker.ui.theme.AmountStyle
import com.example.expensetracker.ui.theme.CardShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseListTab(
    viewModel: GroupViewModel,
    onEditExpense: (Expense) -> Unit
) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val memberMap = remember(members) { members.associateBy { it.id } }
    val categoryIconMap = remember { expenseCategories.associate { it.name to it.icon } }
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No expenses logged yet.\nTap + to quickly add one!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(expenses, key = { it.id }) { expense ->
                val payerName = memberMap[expense.paidByMemberId]?.name ?: "Member ${expense.paidByMemberId}"
                val catIcon = categoryIconMap[expense.category] ?: "💰"
                val dateStr = dateFormatter.format(Date(expense.createdAt))
                val interactionSource = rememberPressInteractionSource()

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
                                modifier = Modifier.size(44.dp)
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
                                    text = "Paid by $payerName • $dateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = CurrencyUtils.formatAmountStyled(
                                    expense.amount,
                                    expense.currency,
                                    symbolColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                style = AmountStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FilledTonalIconButton(
                                onClick = { expenseToDelete = expense },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete Expense"
                                )
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
            icon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Expense") },
            text = { Text("Are you sure you want to delete this expense? This will update all balances.") },
            confirmButton = {
                Button(
                    onClick = {
                        expenseToDelete?.let { viewModel.deleteExpense(it) }
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
