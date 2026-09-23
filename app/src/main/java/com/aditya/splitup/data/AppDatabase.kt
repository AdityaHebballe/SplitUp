package com.aditya.splitup.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aditya.splitup.data.dao.ExchangeRateDao
import com.aditya.splitup.data.dao.ExpenseDao
import com.aditya.splitup.data.dao.GroupDao
import com.aditya.splitup.data.dao.MemberDao
import com.aditya.splitup.data.dao.PaymentDao
import com.aditya.splitup.data.model.ExchangeRate
import com.aditya.splitup.data.model.Expense
import com.aditya.splitup.data.model.ExpenseSplit
import com.aditya.splitup.data.model.Member
import com.aditya.splitup.data.model.Payment
import com.aditya.splitup.data.model.SplitGroup

@Database(
    entities = [
        SplitGroup::class,
        Member::class,
        Expense::class,
        ExpenseSplit::class,
        Payment::class,
        ExchangeRate::class
    ],
    version = 3,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SplitGroup
                db.execSQL("ALTER TABLE groups ADD COLUMN firestoreId TEXT")
                db.execSQL("ALTER TABLE groups ADD COLUMN ownerUid TEXT")
                // Member
                db.execSQL("ALTER TABLE members ADD COLUMN firestoreId TEXT")
                db.execSQL("ALTER TABLE members ADD COLUMN linkedUid TEXT")
                // Expense
                db.execSQL("ALTER TABLE expenses ADD COLUMN firestoreId TEXT")
                db.execSQL("ALTER TABLE expenses ADD COLUMN addedByUid TEXT")
                // ExpenseSplit
                db.execSQL("ALTER TABLE expense_splits ADD COLUMN firestoreId TEXT")
                // Payment
                db.execSQL("ALTER TABLE payments ADD COLUMN firestoreId TEXT")
                db.execSQL("ALTER TABLE payments ADD COLUMN addedByUid TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE members ADD COLUMN isRemoved INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "split_tracker_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
