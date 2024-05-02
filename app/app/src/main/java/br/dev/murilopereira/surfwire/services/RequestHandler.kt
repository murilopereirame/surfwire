package br.dev.murilopereira.surfwire.services

import android.content.Context
import android.text.TextUtils
import android.util.Patterns
import android.webkit.URLUtil
import android.widget.Toast
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class RequestHandler(endpoint: String?) {
    private var service: SurfwireService?;

    init {
        service = if (!endpoint.isNullOrEmpty()) {
            setupService(endpoint)
        } else {
            null
        }
    }

    private fun setupService(url: String): SurfwireService {
        val retro = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        return retro.create(SurfwireService::class.java)
    }

    suspend fun updateUrl(context: Context, url: String) {
        DataStoreSingleton.getInstance(context).setUrl(url)
        if (URLUtil.isValidUrl(url) && Patterns.WEB_URL.matcher(url).matches()) {
            service = setupService(url)
        }
    }

    fun status(context: Context, callback: (boolean: Boolean, body: Status?) -> Unit) {
        val statusRequest = service?.status()
        statusRequest?.enqueue(object : Callback<Status> {
            override fun onResponse(call: Call<Status>, response: Response<Status>) {
                callback(response.isSuccessful, response.body())
            }

            override fun onFailure(call: Call<Status>, error: Throwable) {
                handleErrorMessages(context, listOf(error.message))
                callback(false, null)
            }
        })
    }

    fun connect(context: Context, callback: (boolean: Boolean, body: Toggle?) -> Unit) {
        val statusRequest = service?.connect()
        statusRequest?.enqueue(object : Callback<Toggle> {
            override fun onResponse(call: Call<Toggle>, response: Response<Toggle>) {
                callback(response.isSuccessful, response.body())

                if (!response.isSuccessful) {
                    val messages = JSONObject(response.errorBody()!!.string())
                    if (messages.has("errors")) {
                        handleErrorMessages(context, messages.get("errors"))
                    }
                }
            }

            override fun onFailure(call: Call<Toggle>, error: Throwable) {
                handleErrorMessages(context, listOf(error.message))
                callback(false, null)
            }
        })
    }

    fun disconnect(context: Context, callback: (boolean: Boolean, body: Toggle?) -> Unit) {
        val statusRequest = service?.disconnect()
        statusRequest?.enqueue(object : Callback<Toggle> {
            override fun onResponse(call: Call<Toggle>, response: Response<Toggle>) {
                callback(response.isSuccessful, response.body())

                if (!response.isSuccessful) {
                    val messages = JSONObject(response.errorBody()!!.string())
                    if (messages.has("errors")) {
                        handleErrorMessages(context, messages.get("errors"))
                    }
                }
            }

            override fun onFailure(call: Call<Toggle>, error: Throwable) {
                handleErrorMessages(context, listOf(error.message))
                callback(false, null)
            }
        })
    }

    private fun handleErrorMessages(context: Context, messages: Any) {
        if (messages is Array<*> && messages.isArrayOf<String>()) {
            Toast.makeText(context, TextUtils.join(". ", messages), Toast.LENGTH_LONG).show()
        } else if (messages is List<*>) {
            Toast.makeText(context, TextUtils.join(". ", messages), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Failed to parse error message", Toast.LENGTH_LONG).show()
        }
    }
}