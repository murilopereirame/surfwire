package br.dev.murilopereira.surfwire

import DialogSingleton
import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import br.dev.murilopereira.surfwire.services.DataStoreSingleton
import br.dev.murilopereira.surfwire.services.RequestHandler
import br.dev.murilopereira.surfwire.services.Status
import br.dev.murilopereira.surfwire.ui.dialogs.LoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private var connected: Boolean = false
    private var loadingDialog: LoadingDialog? = null
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val requestHandler = RequestHandler("")
    private var scheduledStatusCheck: ScheduledFuture<*>? = null
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
                if (connected) {
                    disconnect()
                } else {
                    connect()
                }
            }
        }

        findViewById<Button>(R.id.refreshStatus).setOnClickListener {
            CoroutineScope(IO).launch {
                updateStatus()
            }
        }

        findViewById<EditText>(R.id.serverInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

            override fun afterTextChanged(s: Editable?) {
                CoroutineScope(IO).launch {
                    requestHandler.updateUrl(applicationContext, s.toString())
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
        val url: String? = DataStoreSingleton.getInstance(applicationContext).getUrl()

        if (!url.isNullOrEmpty()) {
            if (URLUtil.isValidUrl(url) && Patterns.WEB_URL.matcher(url).matches()) {
                updateStatus()
                if (scheduledStatusCheck == null || scheduledStatusCheck?.isCancelled == true) {
                    scheduledStatusCheck = worker.scheduleAtFixedRate(
                        {
                            this.requestHandler.status(this) { isSuccessful, status ->
                                updateStatusCallback(
                                    isSuccessful,
                                    status
                                )
                            }
                        },
                        5,
                        5,
                        TimeUnit.SECONDS
                    )
                }
            }

            runOnUiThread {
                findViewById<EditText>(R.id.serverInput).setText(url)
            }
        }
    }

    private fun handleLoadingDialog(show: Boolean) {
        runOnUiThread { if (show) loadingDialog?.show() else loadingDialog?.dismiss() }
    }

    private fun updateStatus() {
        handleLoadingDialog(show = true)
        requestHandler.status(this) { isSuccessful, status ->
            updateStatusCallback(isSuccessful, status)
            handleLoadingDialog(show = false)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateStatusCallback(
        isSuccessful: Boolean,
        status: Status?
    ) {
        if (isSuccessful && status != null) {
            val statusTextField = findViewById<TextView>(R.id.serverLogs)
            statusTextField.text = "" +
                    "Client pKey: ${status.client}\n" +
                    "Endpoint: ${status.endpoint}\n" +
                    "Routed IPs: ${status.allowedIps}\n" +
                    "Handshake: ${status.uptime}\n" +
                    "Received: ${"%.2f".format(status.traffic.received.value)} ${status.traffic.received.unit}\n" +
                    "Sent: ${"%.2f".format(status.traffic.sent.value)} ${status.traffic.sent.unit}\n"

            val button = findViewById<Button>(R.id.connectionToggleButton)
            button.text = "DISCONNECT"
            button.background.setTint(getColor(R.color.red))

            connected = true
        } else {
            val button = findViewById<Button>(R.id.connectionToggleButton)
            button.text = "CONNECT"
            button.background.setTint(getColor(R.color.green))

            scheduledStatusCheck?.cancel(true)

            connected = false
        }
    }

    private fun disconnect() {
        handleLoadingDialog(show = true)
        val context = this
        requestHandler.disconnect(this) { isSuccessful, status ->
            if (isSuccessful) {
                val button = findViewById<Button>(R.id.connectionToggleButton)
                button.text = "CONNECT"
                button.background.setTint(getColor(R.color.green))

                Toast.makeText(context, "Status: Disconnected", Toast.LENGTH_SHORT).show()
                scheduledStatusCheck?.cancel(true)
                connected = status?.connected == true
            }

            handleLoadingDialog(show = false)
        }
    }

    private fun connect() {
        handleLoadingDialog(show = true)
        val context = this
        requestHandler.connect(this) { isSuccessful, status ->
            if (isSuccessful && status != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    updateStatus()
                    if (scheduledStatusCheck == null || scheduledStatusCheck?.isCancelled == true) {
                        scheduledStatusCheck = worker.scheduleAtFixedRate(
                            {
                                requestHandler.status(context) { isSuccessful, status ->
                                    updateStatusCallback(
                                        isSuccessful,
                                        status
                                    )
                                }
                            },
                            5,
                            5,
                            TimeUnit.SECONDS
                        )
                    }
                }

                val button = findViewById<Button>(R.id.connectionToggleButton)

                button.text = "DISCONNECT"
                button.background.setTint(getColor(R.color.red))

                Toast.makeText(context, "Status: Connected", Toast.LENGTH_SHORT).show()
                connected = status.connected
            }

            handleLoadingDialog(show = false)
        }
    }
}