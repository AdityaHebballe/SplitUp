package com.example.expensetracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

data class CategoryInfo(val name: String, val icon: String)

val expenseCategories = listOf(
    CategoryInfo("Food", "🍕"),
    CategoryInfo("Transport", "🚗"),
    CategoryInfo("Rent", "🏠"),
    CategoryInfo("Fun", "🎮"),
    CategoryInfo("Shop", "🛒"),
    CategoryInfo("Utilities", "⚡"),
    CategoryInfo("Health", "💊"),
    CategoryInfo("Travel", "✈️"),
    CategoryInfo("Other", "📦")
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        expenseCategories.forEach { category ->
            ElevatedFilterChip(
                selected = selectedCategory == category.name,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (selectedCategory == category.name) {
                        onCategorySelected(null)
                    } else {
                        onCategorySelected(category.name)
                    }
                },
                label = { Text("${category.icon} ${category.name}") }
            )
        }
    }
}
