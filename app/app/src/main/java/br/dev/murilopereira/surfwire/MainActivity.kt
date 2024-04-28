package br.dev.murilopereira.surfwire

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.dev.murilopereira.surfwire.services.DataStoreSingleton
import br.dev.murilopereira.surfwire.services.Status
import br.dev.murilopereira.surfwire.services.SurfwireService
import br.dev.murilopereira.surfwire.services.Toggle
import br.dev.murilopereira.surfwire.ui.dialogs.LoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : AppCompatActivity() {
	private lateinit var service: SurfwireService
	private var connected: Boolean = false
	private var loadingDialog: LoadingDialog? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		enableEdgeToEdge()

		loadingDialog = DialogSingleton.getLoadingDialog(this)

		handleLoadingDialog(show = true)

		CoroutineScope(IO).launch {
			startUpServices()
			handleLoadingDialog(show = false)
		}


		findViewById<Button>(R.id.connectionToggleButton).setOnClickListener {
			CoroutineScope(IO).launch {
				if(connected) {
					disconnect()
				} else {
					connect()
				}
			}
		}

		findViewById<Button>(R.id.refreshStatus).setOnClickListener {
			CoroutineScope(IO).launch {
				updateStatus(isStartupRequest = false)
			}
		}

		findViewById<EditText>(R.id.serverInput).addTextChangedListener (object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

			}

			override fun afterTextChanged(s: Editable?) {
				Log.d("CHANGE", s.toString())
				CoroutineScope(IO).launch {
					setServerUrl(s.toString())
				}
			}
		})

		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
			val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
			insets
		}
	}

	private suspend fun startUpServices() {
		val url: String? = DataStoreSingleton.getInstance(this).getUrl()

		if(!url.isNullOrEmpty()) {
			if(URLUtil.isValidUrl(url) && Patterns.WEB_URL.matcher(url).matches()) {
				Log.d("DONE", "URL: $url")
				setupService(url)
				updateStatus(true)
			}

			runOnUiThread {
				findViewById<EditText>(R.id.serverInput).setText(url)
			}
		}
	}

	private suspend fun setServerUrl(url: String) {
		DataStoreSingleton.getInstance(this).setUrl(url)
		if(URLUtil.isValidUrl(url) && Patterns.WEB_URL.matcher(url).matches()) {
			setupService(url)
		}
	}

	private fun handleLoadingDialog(show: Boolean) {
		runOnUiThread { if(show) loadingDialog?.show() else loadingDialog?.dismiss() }
	}

	private fun setupService(endpoint: String) {
		val retro = Retrofit.Builder()
			.baseUrl(endpoint)
			.addConverterFactory(MoshiConverterFactory.create())
			.build()
		service = retro.create(SurfwireService::class.java)
	}

	private fun updateStatus(isStartupRequest: Boolean) {
		if(!this::service.isInitialized) {
			return Toast.makeText(this, "Service not initialized yet. Verify if the URL is set", Toast.LENGTH_SHORT).show()
		}

		handleLoadingDialog(show = true)
		val statusRequest = service.status()
		statusRequest.enqueue(object : Callback<Status> {
			@SuppressLint("SetTextI18n")
			override fun onResponse(call: Call<Status>, response: Response<Status>) {
				if(response.isSuccessful) {
					val status: Status = response.body()!!
					val statusTextField = findViewById<TextView>(R.id.serverLogs)
					statusTextField.text = "" +
						"Client pKey: ${status.client}\n"+
           				"Endpoint: ${status.endpoint}\n"+
						"Routed IPs: ${status.allowedIps}\n"+
           				"Uptime: ${status.uptime}\n"+
           				"Received: ${"%.2f".format(status.traffic.received.value)} ${status.traffic.received.unit}\n"+
           				"Sent: ${"%.2f".format(status.traffic.sent.value)} ${status.traffic.sent.unit}\n"

					val button = findViewById<Button>(R.id.connectionToggleButton)
					button.text = "DISCONNECT"
					button.background.setTint(getColor(R.color.red))

					connected = true
				} else {
					val messages = JSONObject(response.errorBody()!!.string()).get("errors")
					if (!isStartupRequest) {
						handleErrorMessages(messages)
					}

					val button = findViewById<Button>(R.id.connectionToggleButton)
					button.text = "CONNECT"
					button.background.setTint(getColor(R.color.green))

					connected = false
				}

				handleLoadingDialog(show = false)
			}

			override fun onFailure(call: Call<Status>, error: Throwable) {
				handleLoadingDialog(show = false)
				handleErrorMessages(listOf(error.toString()))
				connected = false
			}
		})
	}

	private fun disconnect() {
		if(!this::service.isInitialized) {
			return Toast.makeText(this, "Service not initialized yet. Verify if the URL is set", Toast.LENGTH_SHORT).show()
		}

		handleLoadingDialog(show = true)
		val statusRequest = service.disconnect()
		val context = this
		statusRequest.enqueue(object : Callback<Toggle> {
			override fun onResponse(call: Call<Toggle>, response: Response<Toggle>) {
				connected = if (response.isSuccessful) {
					val status: Toggle = response.body()!!
					val button = findViewById<Button>(R.id.connectionToggleButton)
					button.text = "CONNECT"
					button.background.setTint(getColor(R.color.green))

					Toast.makeText(context, "Status: Disconnected", Toast.LENGTH_SHORT).show()
					status.connected
				} else {
					val messages = JSONObject(response.errorBody()!!.string()).get("messages")
					handleErrorMessages(messages)
					true
				}

				handleLoadingDialog(show = false)
			}

			override fun onFailure(call: Call<Toggle>, error: Throwable) {
				handleLoadingDialog(show = false)
				handleErrorMessages(listOf(error.message))
				val button = findViewById<Button>(R.id.connectionToggleButton)
				button.text = "DISCONNECT"
				button.background = ColorDrawable(getColor(R.color.red))
				connected = true
			}
		})
	}

	private fun connect() {
		if(!this::service.isInitialized) {
			return Toast.makeText(this, "Service not initialized yet. Verify if the URL is set", Toast.LENGTH_SHORT).show()
		}

		handleLoadingDialog(show = true)
		val statusRequest = service.connect()
		val context = this
		statusRequest.enqueue(object : Callback<Toggle> {
			override fun onResponse(call: Call<Toggle>, response: Response<Toggle>) {
				connected = if(response.isSuccessful) {
					val status: Toggle = response.body()!!

					CoroutineScope(IO).launch {
						updateStatus(false)
					}
					val button = findViewById<Button>(R.id.connectionToggleButton)

					button.text = "DISCONNECT"
					button.background = ColorDrawable(getColor(R.color.red))

					Toast.makeText(context, "Status: Connected", Toast.LENGTH_SHORT).show()
					status.connected
				} else {
					val messages = JSONObject(response.errorBody()!!.string()).get("messages")
					handleErrorMessages(messages)
					val button = findViewById<Button>(R.id.connectionToggleButton)
					button.text = "CONNECT"
					button.background.setTint(getColor(R.color.green))
					handleLoadingDialog(show = false)
					false
				}
			}

			override fun onFailure(call: Call<Toggle>, error: Throwable) {
				handleLoadingDialog(show = false)
				handleErrorMessages(listOf(error.message))
				connected = false
			}
		})
	}

	private fun handleErrorMessages(messages: Any) {
		if(messages is Array<*> && messages.isArrayOf<String>()) {
			Toast.makeText(this, TextUtils.join(". ", messages), Toast.LENGTH_LONG).show()
		} else if(messages is List<*>) {
			Toast.makeText(this, TextUtils.join(". ", messages), Toast.LENGTH_LONG).show()
		} else {
			Toast.makeText(this, "Failed to parse error message", Toast.LENGTH_LONG).show()
		}
	}
}