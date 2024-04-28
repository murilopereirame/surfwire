package br.dev.murilopereira.surfwire.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreSingleton(private val context: Context) {
    companion object {
        private var instance: DataStoreSingleton? = null
        fun getInstance(context: Context): DataStoreSingleton {
            if (instance == null) {
                instance = DataStoreSingleton(context)
            }

            return instance!!
        }
    }

    private val SERVER_URL = stringPreferencesKey("server_url")
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "surfwire_settings")

    suspend fun getUrl(): String? {
        if(instance == null) return null

        return instance!!.context.dataStore.data.map { preferences -> preferences[SERVER_URL] }.first()
    }

    suspend fun setUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVER_URL] = url
        }
    }
}