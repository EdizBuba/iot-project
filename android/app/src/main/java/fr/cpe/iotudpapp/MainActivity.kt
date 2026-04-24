package fr.cpe.iotudpapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var textServerInfo: TextView
    private lateinit var buttonChangeServer: TextView
    private lateinit var buttonGet: Button
    private lateinit var buttonModeTLH: MaterialButton
    private lateinit var buttonModeLTH: MaterialButton
    private lateinit var buttonModeTHL: MaterialButton
    private lateinit var textStatus: TextView
    private lateinit var textTemperature: TextView
    private lateinit var textLuminosite: TextView
    private lateinit var textHumidite: TextView
    private lateinit var textResponse: TextView

    private lateinit var serverIp: String
    private var serverPort: Int = -1

    companion object {
        const val EXTRA_SERVER_IP = "extra_server_ip"
        const val EXTRA_SERVER_PORT = "extra_server_port"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textServerInfo = findViewById(R.id.textServerInfo)
        buttonChangeServer = findViewById(R.id.buttonChangeServer)
        buttonGet = findViewById(R.id.buttonGet)
        buttonModeTLH = findViewById(R.id.buttonModeTLH)
        buttonModeLTH = findViewById(R.id.buttonModeLTH)
        buttonModeTHL = findViewById(R.id.buttonModeTHL)
        textStatus = findViewById(R.id.textStatus)
        textTemperature = findViewById(R.id.textTemperature)
        textLuminosite = findViewById(R.id.textLuminosite)
        textHumidite = findViewById(R.id.textHumidite)
        textResponse = findViewById(R.id.textResponse)

        buttonChangeServer.setOnClickListener {
            val intent = android.content.Intent(this, ConnectionActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        textStatus.text = "● Actif"
        textTemperature.text = "--"
        textLuminosite.text = "--"
        textHumidite.text = "--"

        buttonGet.setOnClickListener {
            sendUdpMessage("GET")
        }

        buttonModeTLH.setOnClickListener { sendModeCommand("TLH", buttonModeTLH) }
        buttonModeLTH.setOnClickListener { sendModeCommand("LTH", buttonModeLTH) }
        buttonModeTHL.setOnClickListener { sendModeCommand("THL", buttonModeTHL) }

        updateModeVisualSelection(null)
    }

    private fun sendUdpMessage(message: String) {
        if (serverPort !in 1..65535 || serverIp.isEmpty()) {
            showStatusError(getString(R.string.error_invalid_connection_payload))
            return
        }

        textStatus.text = getString(R.string.status_sending, message)

        thread {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 3000

                val serverAddress = InetAddress.getByName(serverIp)
                val sendData = message.toByteArray(Charsets.UTF_8)

                val sendPacket = DatagramPacket(
                    sendData,
                    sendData.size,
                    serverAddress,
                    serverPort
                )

                socket.send(sendPacket)

                val receiveBuffer = ByteArray(4096)
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
                runOnUiThread {
                    showStatusError(getString(R.string.error_timeout))
                }
            } catch (_: UnknownHostException) {
                runOnUiThread {
                    showStatusError(getString(R.string.error_unknown_host))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    val messageError = e.message ?: "inconnue"
                    showStatusError(getString(R.string.error_socket, messageError))
                }
            } finally {
                socket?.close()
            }
        }
    }

    private fun handleUdpResponse(response: String) {
        textResponse.text = response

        val values = parseSensorJson(response)
        if (values == null) {
            textStatus.text = getString(R.string.status_json_warning)
            return
        }

        textTemperature.text = values.temperature ?: "--"
        textLuminosite.text = values.luminosite ?: "--"
        textHumidite.text = values.humidite ?: "--"

        textStatus.text = "● Actif"
    }

    private fun parseSensorJson(rawJson: String): SensorValues? {
        return try {
            val json = JSONObject(rawJson)
            val values = SensorValues(
                temperature = json.opt("T")?.takeIf { it != JSONObject.NULL }?.toString(),
                luminosite = json.opt("L")?.takeIf { it != JSONObject.NULL }?.toString(),
                humidite = json.opt("H")?.takeIf { it != JSONObject.NULL }?.toString()
            )

            if (values.temperature == null && values.luminosite == null && values.humidite == null) {
                null
            } else {
                values
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun showStatusError(message: String) {
        textStatus.text = message
    }

    private fun sendModeCommand(mode: String, selectedButton: MaterialButton) {
        updateModeVisualSelection(selectedButton)
        textStatus.text = "● Actif"
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

    private data class SensorValues(
        val temperature: String?,
        val luminosite: String?,
        val humidite: String?
    )
}