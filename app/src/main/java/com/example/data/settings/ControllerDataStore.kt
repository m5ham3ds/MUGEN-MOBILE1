package com.example.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

object ControllerDataStore {
    private val CONTROLLER_LAYOUT = stringPreferencesKey("controller_layout")
    private val CONTROLLER_SCALE = stringPreferencesKey("controller_scale")
    private val DPAD_GROUPED = androidx.datastore.preferences.core.booleanPreferencesKey("dpad_grouped")
    private val LEFT_TOOLBAR_ORDER = stringPreferencesKey("left_toolbar_order")
    private val RIGHT_TOOLBAR_ORDER = stringPreferencesKey("right_toolbar_order")

    val DEFAULT_LEFT_TOOLS = listOf("MOVE", "SIZE", "SHAPE", "OPACITY", "DELETE")
    val DEFAULT_RIGHT_TOOLS = listOf("GRID", "SNAP", "PREVIEW", "DPAD_GROUP", "BUTTONS_MANAGER")

    fun getLayout(context: Context): Flow<String?> {
        return context.dataStore.data.map { preferences ->
            preferences[CONTROLLER_LAYOUT]
        }
    }

    suspend fun saveLayout(context: Context, layoutString: String) {
        context.dataStore.edit { preferences ->
            preferences[CONTROLLER_LAYOUT] = layoutString
        }
    }
    
    fun getScale(context: Context): Flow<Float> {
        return context.dataStore.data.map { preferences ->
            preferences[CONTROLLER_SCALE]?.toFloatOrNull() ?: 1.0f
        }
    }

    suspend fun saveScale(context: Context, scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[CONTROLLER_SCALE] = scale.toString()
        }
    }

    fun isDpadGrouped(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DPAD_GROUPED] ?: true
        }
    }

    suspend fun saveDpadGrouped(context: Context, grouped: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DPAD_GROUPED] = grouped
        }
    }

    fun getLeftToolbarOrder(context: Context): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            val saved = preferences[LEFT_TOOLBAR_ORDER]
            if (saved.isNullOrEmpty()) {
                DEFAULT_LEFT_TOOLS
            } else {
                try {
                    val arr = JSONArray(saved)
                    val list = (0 until arr.length()).map { arr.getString(it) }
                    // Filter to ensure all valid tools exist and no unrecognized tools corrupt
                    val validList = list.filter { it in DEFAULT_LEFT_TOOLS }
                    val missing = DEFAULT_LEFT_TOOLS.filter { it !in validList }
                    validList + missing
                } catch (e: Exception) {
                    DEFAULT_LEFT_TOOLS
                }
            }
        }
    }

    suspend fun saveLeftToolbarOrder(context: Context, order: List<String>) {
        context.dataStore.edit { preferences ->
            val arr = JSONArray(order)
            preferences[LEFT_TOOLBAR_ORDER] = arr.toString()
        }
    }

    fun getRightToolbarOrder(context: Context): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            val saved = preferences[RIGHT_TOOLBAR_ORDER]
            if (saved.isNullOrEmpty()) {
                DEFAULT_RIGHT_TOOLS
            } else {
                try {
                    val arr = JSONArray(saved)
                    val list = (0 until arr.length()).map { arr.getString(it) }
                    val validList = list.filter { it in DEFAULT_RIGHT_TOOLS }
                    val missing = DEFAULT_RIGHT_TOOLS.filter { it !in validList }
                    validList + missing
                } catch (e: Exception) {
                    DEFAULT_RIGHT_TOOLS
                }
            }
        }
    }

    suspend fun saveRightToolbarOrder(context: Context, order: List<String>) {
        context.dataStore.edit { preferences ->
            val arr = JSONArray(order)
            preferences[RIGHT_TOOLBAR_ORDER] = arr.toString()
        }
    }
}
