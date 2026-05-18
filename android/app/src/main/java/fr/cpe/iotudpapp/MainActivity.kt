package fr.cpe.iotudpapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var textServerInfo: TextView
    private lateinit var textLastReceived: TextView
    private lateinit var buttonChangeServer: TextView
    private lateinit var buttonGet: Button
    private lateinit var buttonModeTLH: MaterialButton
    private lateinit var buttonModeLTH: MaterialButton
    private lateinit var buttonModeTHL: MaterialButton
    private lateinit var textStatus: TextView
    private lateinit var textTemperature: TextView
    private lateinit var textLuminosite: TextView
    private lateinit var textHumidite: TextView
    private lateinit var textPression: TextView
    private lateinit var textResponse: TextView

    private lateinit var serverIp: String
    private var serverPort: Int = -1

    @Volatile
    private var isListening = false

    @Volatile
    private var udpSocket: DatagramSocket? = null

    @Volatile
    private var serverAddress: InetAddress? = null

    companion object {
        const val EXTRA_SERVER_IP = "extra_server_ip"
        const val EXTRA_SERVER_PORT = "extra_server_port"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textServerInfo = findViewById(R.id.textServerInfo)
        textLastReceived = findViewById(R.id.textLastReceived)
        buttonChangeServer = findViewById(R.id.buttonChangeServer)
        buttonGet = findViewById(R.id.buttonGet)
        buttonModeTLH = findViewById(R.id.buttonModeTLH)
        buttonModeLTH = findViewById(R.id.buttonModeLTH)
        buttonModeTHL = findViewById(R.id.buttonModeTHL)
        textStatus = findViewById(R.id.textStatus)
        textTemperature = findViewById(R.id.textTemperature)
        textLuminosite = findViewById(R.id.textLuminosite)
        textHumidite = findViewById(R.id.textHumidite)
        textPression = findViewById(R.id.textPression)
        textResponse = findViewById(R.id.textResponse)

        buttonChangeServer.setOnClickListener {
            val intent = Intent(this, ConnectionActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        serverIp = intent.getStringExtra(EXTRA_SERVER_IP).orEmpty().trim()
        serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, -1)

        if (serverIp.isEmpty() || serverPort !in 1..65535) {
            textServerInfo.text = getString(R.string.server_info_invalid)
            showStatusError(getString(R.string.error_invalid_connection_payload))
            setControlsEnabled(false)
            return
        }

        textServerInfo.text = getString(R.string.server_info_format, serverIp, serverPort)
        textStatus.text = getString(R.string.status_connecting)
        textTemperature.text = "--"
        textLuminosite.text = "--"
        textHumidite.text = "--"
        textPression.text = "--"

        buttonGet.setOnClickListener {
            sendUdpMessage("GET")
        }

        buttonModeTLH.setOnClickListener { sendModeCommand("TLH", buttonModeTLH) }
        buttonModeLTH.setOnClickListener { sendModeCommand("LTH", buttonModeLTH) }
        buttonModeTHL.setOnClickListener { sendModeCommand("THL", buttonModeTHL) }

        updateModeVisualSelection(null)
        startUdpSession()
    }

    override fun onDestroy() {
        stopUdpSession()
        super.onDestroy()
    }

    private fun startUdpSession() {
        if (isListening) {
            return
        }

        isListening = true

        thread(name = "udp-listener") {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 1000
                serverAddress = InetAddress.getByName(serverIp)
                udpSocket = socket

                sendUdpMessageInternal("subscribe()", socket)
                sendUdpMessageInternal("GET", socket)

                val receiveBuffer = ByteArray(4096)
                while (isListening) {
                    try {
                        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(receivePacket)

                        val response = String(
                            receivePacket.data,
                            0,
                            receivePacket.length,
                            Charsets.UTF_8
                        )

                        runOnUiThread {
                            handleUdpResponse(response)
                        }
                    } catch (_: SocketTimeoutException) {
                    } catch (_: SocketException) {
                        if (isListening) {
                            throw SocketException("Socket UDP interrompue")
                        }
                    }
                }
            } catch (_: UnknownHostException) {
                runOnUiThread {
                    showStatusError(getString(R.string.error_unknown_host))
                }
            } catch (e: Exception) {
                if (isListening) {
                    runOnUiThread {
                        showStatusError(getString(R.string.error_socket, e.message ?: "inconnue"))
                    }
                }
            } finally {
                socket?.close()
                udpSocket = null
            }
        }
    }

    private fun stopUdpSession() {
        isListening = false
        udpSocket?.close()
        udpSocket = null
    }

    private fun sendUdpMessage(message: String) {
        if (serverPort !in 1..65535 || serverIp.isEmpty()) {
            showStatusError(getString(R.string.error_invalid_connection_payload))
            return
        }

        textStatus.text = getString(R.string.status_sending, message)

        thread {
            try {
                val socket = udpSocket
                if (socket == null) {
                    runOnUiThread {
                        showStatusError(getString(R.string.error_socket_not_ready))
                    }
                    return@thread
                }

                sendUdpMessageInternal(message, socket)
            } catch (_: UnknownHostException) {
                runOnUiThread {
                    showStatusError(getString(R.string.error_unknown_host))
                }
            } catch (_: SocketException) {
                runOnUiThread {
                    showStatusError(getString(R.string.error_socket_not_ready))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showStatusError(getString(R.string.error_socket, e.message ?: "inconnue"))
                }
            }
        }
    }

    private fun sendUdpMessageInternal(message: String, socket: DatagramSocket) {
        val address = serverAddress ?: InetAddress.getByName(serverIp).also { serverAddress = it }
        val sendData = message.toByteArray(Charsets.UTF_8)
        val sendPacket = DatagramPacket(sendData, sendData.size, address, serverPort)
        socket.send(sendPacket)
    }

    private fun handleUdpResponse(response: String) {
        textResponse.text = response

        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        textLastReceived.text = getString(R.string.last_received_format, time)
        textLastReceived.visibility = View.VISIBLE

        val message = parseGatewayMessage(response)
        if (message == null) {
            textStatus.text = getString(R.string.status_json_warning)
            return
        }

        message.sensorValues?.let { values ->
            textTemperature.text = values.temperature ?: "--"
            textLuminosite.text = values.luminosite ?: "--"
            textHumidite.text = values.humidite ?: "--"
            textPression.text = values.pression ?: "--"
        }

        when (message.type) {
            "data" -> textStatus.text = getString(R.string.status_data_updated)
            "subscribed" -> textStatus.text = getString(R.string.status_connected)
            "config_sent" -> textStatus.text = getString(R.string.status_config_sent, message.order ?: "--")
            "config_ack" -> {
                textStatus.text = getString(R.string.status_config_ack, message.order ?: "--")
                updateModeVisualSelection(buttonForMode(message.order))
            }
            "config_error", "error" -> {
                showStatusError(message.message ?: getString(R.string.error_generic, "commande refusée"))
            }
            "status" -> textStatus.text = getString(R.string.status_connected)
            else -> textStatus.text = getString(R.string.status_connected)
        }
    }

    private fun parseGatewayMessage(rawJson: String): GatewayMessage? {
        return try {
            val json = JSONObject(rawJson)
            val sensorValues = parseSensorValues(json)
            GatewayMessage(
                type = json.optString("type").ifBlank {
                    if (sensorValues != null) {
                        "data"
                    } else {
                        "unknown"
                    }
                },
                order = json.optionalString("order"),
                message = json.optionalString("message"),
                sensorValues = sensorValues
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSensorValues(json: JSONObject): SensorValues? {
        val directValues = SensorValues(
            temperature = json.firstValue("temperature", "T"),
            luminosite = json.firstValue("light", "L"),
            humidite = json.firstValue("humidity", "H"),
            pression = json.firstValue("pressure", "P")
        )
        if (directValues.hasAnyValue()) {
            return directValues
        }

        val nested = json.optJSONObject("latest_data") ?: return null
        val nestedValues = SensorValues(
            temperature = nested.firstValue("temperature", "T"),
            luminosite = nested.firstValue("light", "L"),
            humidite = nested.firstValue("humidity", "H"),
            pression = nested.firstValue("pressure", "P")
        )
        return if (nestedValues.hasAnyValue()) nestedValues else null
    }

    private fun buttonForMode(mode: String?): MaterialButton? {
        return when (mode) {
            "TLH" -> buttonModeTLH
            "LTH" -> buttonModeLTH
            "THL" -> buttonModeTHL
            else -> null
        }
    }

    private fun JSONObject.optionalString(key: String): String? {
        val value = opt(key)
        return if (value == null || value == JSONObject.NULL) {
            null
        } else {
            value.toString()
        }
    }

    private fun JSONObject.firstValue(vararg keys: String): String? {
        for (key in keys) {
            val value = optionalString(key)
            if (value != null) {
                return value
            }
        }
        return null
    }

    private fun SensorValues.hasAnyValue(): Boolean {
        return temperature != null || luminosite != null || humidite != null || pression != null
    }

    private fun showStatusError(message: String) {
        textStatus.text = message
    }

    private fun sendModeCommand(mode: String, selectedButton: MaterialButton) {
        updateModeVisualSelection(selectedButton)
        textStatus.text = getString(R.string.status_sending, mode)
        sendUdpMessage(mode)
    }

    private fun updateModeVisualSelection(selectedButton: MaterialButton?) {
        val buttons = listOf(buttonModeTLH, buttonModeLTH, buttonModeTHL)
        buttons.forEach { button ->
            button.isChecked = button == selectedButton
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {
        buttonGet.isEnabled = enabled
        buttonModeTLH.isEnabled = enabled
        buttonModeLTH.isEnabled = enabled
        buttonModeTHL.isEnabled = enabled
    }

    private data class GatewayMessage(
        val type: String,
        val order: String?,
        val message: String?,
        val sensorValues: SensorValues?
    )

    private data class SensorValues(
        val temperature: String?,
        val luminosite: String?,
        val humidite: String?,
        val pression: String?
    )
}
