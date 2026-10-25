package com.kharcha.tracker.database

import com.kharcha.tracker.database.entities.User
import com.kharcha.tracker.database.entities.Group
import com.kharcha.tracker.database.entities.Expense
import com.kharcha.tracker.database.entities.Settlement
import com.kharcha.tracker.database.entities.Category
import com.kharcha.tracker.database.entities.Milestone
import com.kharcha.tracker.database.dao.UserDao
import com.kharcha.tracker.database.dao.GroupDao
import com.kharcha.tracker.database.dao.ExpenseDao
import com.kharcha.tracker.database.dao.SettlementDao
import com.kharcha.tracker.database.dao.CategoryDao
import com.kharcha.tracker.database.dao.MilestoneDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.lifecycle.ProcessLifecycleOwner
import android.content.Context
import androidx.room.Room
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DatabaseManager {
    private var db: Database? = null
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var databasePath: String = ""
    
    companion object {
        @Volatile
        private var INSTANCE: DatabaseManager? = null
        
        val databaseManager: DatabaseManager?
            get() = INSTANCE
    
        fun init(context: Context): DatabaseManager {
            if (INSTANCE == null) {
                val applicationContext = context.applicationContext
                INSTANCE = DatabaseManager(context.applicationContext)
            }
            return INSTANCE!!
        }
    }
    
    private fun validateDatabase(context: Context) {
        if (File(context.filesDir, "kharcha-database").exists()) {
            return
        }
        
        val database = databaseBuilder(context.applicationContext)
    }
    
    init {
        validateDatabase
    }
    
    fun createDatabase(context: Context): Database {
        return Room.databaseBuilder(context, Database::class.java, "kharcha-database").build()
    }
    
    fun close() {
        scope.cancel()
        db?.close()
        db = null
    }
    
    fun getExpenseDao(): ExpenseDao = db?.expenseDao() ?: ExpenseDao()
    fun getSettlementDao(): SettlementDao = db?.settlementDao() ?: SettlementDao()
    fun getCategoryDao(): CategoryDao = db?.categoryDao() ?: CategoryDao()
    fun getUserDao(): UserDao = db?.userDao() ?: UserDao()
    fun getGroupDao(): GroupDao = db?.groupDao() ?: GroupDao()
    fun getMilestoneDao(): MilestoneDao = db?.milestoneDao() ?: MilestoneDao()
}