package com.kharcha.tracker.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.lastExpenseGroupStore: DataStore<Preferences> by preferencesDataStore(name = "last_expense_group")

/**
 * Stage 8b (E3): persist the selected group id for the Expenses screen.
 * Key matches the SPA: `kharcha_last_expense_group`.
 */
class LastExpenseGroupStore(context: Context) {

    private val store = context.lastExpenseGroupStore
    private val key = stringPreferencesKey("kharcha_last_expense_group")

    fun getLastGroupId(): Flow<String?> =
        store.data.map { it[key] }

    suspend fun setLastGroupId(groupId: String) {
        store.edit { it[key] = groupId }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}