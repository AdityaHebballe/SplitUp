package com.example.expensetracker.domain

import java.util.Locale

data class Currency(val code: String, val name: String, val symbol: String)

data class CategoryInfo(val name: String, val iconName: String)

object CurrencyUtils {
    val currencySymbols = mapOf(
        "USD" to "$", "EUR" to "€", "GBP" to "£", "INR" to "₹", "JPY" to "¥",
        "AUD" to "A$", "CAD" to "C$", "CHF" to "CHF", "CNY" to "¥", "SEK" to "kr",
        "NZD" to "NZ$", "MXN" to "$", "SGD" to "S$", "HKD" to "HK$", "NOK" to "kr",
        "KRW" to "₩", "TRY" to "₺", "RUB" to "₽", "BRL" to "R$", "ZAR" to "R"
    )

    fun getSymbol(currencyCode: String): String {
        return currencySymbols[currencyCode] ?: currencyCode
    }

    fun formatAmount(amount: Double, currencyCode: String): String {
        val symbol = getSymbol(currencyCode)
        return String.format(Locale.US, "%s%.2f", symbol, amount)
    }

    val currencies: List<Currency> = listOf(
        Currency("USD", "US Dollar", "$"),
        Currency("EUR", "Euro", "€"),
        Currency("GBP", "British Pound", "£"),
        Currency("INR", "Indian Rupee", "₹"),
        Currency("JPY", "Japanese Yen", "¥"),
        Currency("AUD", "Australian Dollar", "A$"),
        Currency("CAD", "Canadian Dollar", "C$"),
        Currency("CHF", "Swiss Franc", "CHF"),
        Currency("CNY", "Chinese Yuan", "¥"),
        Currency("SEK", "Swedish Krona", "kr"),
        Currency("NZD", "New Zealand Dollar", "NZ$"),
        Currency("MXN", "Mexican Peso", "$"),
        Currency("SGD", "Singapore Dollar", "S$"),
        Currency("HKD", "Hong Kong Dollar", "HK$"),
        Currency("NOK", "Norwegian Krone", "kr"),
        Currency("KRW", "South Korean Won", "₩"),
        Currency("TRY", "Turkish Lira", "₺"),
        Currency("RUB", "Russian Ruble", "₽"),
        Currency("BRL", "Brazilian Real", "R$"),
        Currency("ZAR", "South African Rand", "R"),
    )

    fun getAllCurrencies(): List<Pair<String, String>> {
        return currencies.map { it.code to it.name }
    }

    val defaultCategories = listOf(
        CategoryInfo("Food", "restaurant"),
        CategoryInfo("Transport", "directions_car"),
        CategoryInfo("Rent", "home"),
        CategoryInfo("Entertainment", "movie"),
        CategoryInfo("Shopping", "shopping_cart"),
        CategoryInfo("Utilities", "bolt"),
        CategoryInfo("Health", "favorite"),
        CategoryInfo("Travel", "flight"),
        CategoryInfo("Other", "category")
    )
}
