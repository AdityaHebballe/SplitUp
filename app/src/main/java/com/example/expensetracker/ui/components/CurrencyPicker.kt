package com.example.expensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.expensetracker.domain.CurrencyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPicker(
    currentCurrency: String,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Currency"
) {
    var expanded by remember { mutableStateOf(false) }

    val currentCurrencyObj = remember(currentCurrency) {
        CurrencyUtils.currencies.find { it.code.equals(currentCurrency, ignoreCase = true) }
    }
    val displayText = if (currentCurrencyObj != null) {
        "${currentCurrencyObj.code} (${currentCurrencyObj.symbol}) - ${currentCurrencyObj.name}"
    } else {
        currentCurrency
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            CurrencyUtils.currencies.forEach { currency ->
                val isSelected = currency.code.equals(currentCurrency, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${currency.code} (${currency.symbol}) — ${currency.name}",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        onCurrencySelected(currency.code)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
