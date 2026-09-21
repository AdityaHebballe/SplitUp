package com.example.expensetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.expensetracker.data.dao.ExchangeRateDao
import com.example.expensetracker.data.dao.ExpenseDao
import com.example.expensetracker.data.dao.GroupDao
import com.example.expensetracker.data.dao.MemberDao
import com.example.expensetracker.data.dao.PaymentDao
import com.example.expensetracker.data.model.ExchangeRate
import com.example.expensetracker.data.model.Expense
import com.example.expensetracker.data.model.ExpenseSplit
import com.example.expensetracker.data.model.Member
import com.example.expensetracker.data.model.Payment
import com.example.expensetracker.data.model.SplitGroup

@Database(
    entities = [
        SplitGroup::class,
        Member::class,
        Expense::class,
        ExpenseSplit::class,
        Payment::class,
        ExchangeRate::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun groupDao(): GroupDao
    abstract fun memberDao(): MemberDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun paymentDao(): PaymentDao
    abstract fun exchangeRateDao(): ExchangeRateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "split_tracker_db"
                )
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
