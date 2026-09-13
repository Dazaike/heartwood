package dev.easonhuang.heartwood.mcp

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom

private val Context.mcpPrefsDataStore by preferencesDataStore(name = "heartwood_mcp")

/** Opt-in state and pairing token for the local, read-only MCP server. */
class McpServerPreferences(private val context: Context) {

    private val ENABLED_KEY = booleanPreferencesKey("enabled")
    private val TOKEN_KEY = stringPreferencesKey("token")

    val enabled: Flow<Boolean> = context.mcpPrefsDataStore.data.map { it[ENABLED_KEY] ?: false }
    val token: Flow<String?> = context.mcpPrefsDataStore.data.map { it[TOKEN_KEY] }

    suspend fun setEnabled(value: Boolean) {
        context.mcpPrefsDataStore.edit { it[ENABLED_KEY] = value }
    }

    /** Returns the persisted token, generating and persisting a new one if absent. */
    suspend fun tokenOrCreate(): String {
        val existing = token.first()
        if (existing != null) return existing
        return regenerateToken()
    }

    /** Generates and persists a fresh 32-hex-char token, replacing any existing one. */
    suspend fun regenerateToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val newToken = bytes.joinToString("") { "%02x".format(it) }
        context.mcpPrefsDataStore.edit { it[TOKEN_KEY] = newToken }
        return newToken
    }
}
