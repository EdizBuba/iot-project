package fr.cpe.iotudpapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.concurrent.thread

class ConnectionActivity : AppCompatActivity() {

    private lateinit var editIp: EditText
    private lateinit var editPort: EditText
    private lateinit var buttonConnect: Button
    private lateinit var textConnectionError: TextView

    private val connectTimeoutMs = 3000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)

        editIp = findViewById(R.id.editIp)
        editPort = findViewById(R.id.editPort)
        buttonConnect = findViewById(R.id.buttonConnect)
        textConnectionError = findViewById(R.id.textConnectionError)

        buttonConnect.setOnClickListener {
            attemptConnection()
        }
    }

    private fun attemptConnection() {
        val ip = editIp.text.toString().trim()
        val portText = editPort.text.toString().trim()

        if (ip.isEmpty()) {
            showError(getString(R.string.error_empty_ip))
            return
        }

        if (!isValidIpv4(ip)) {
            showError(getString(R.string.error_invalid_ip))
            return
        }

        if (portText.isEmpty()) {
            showError(getString(R.string.error_empty_port))
            return
        }

        val port = try {
            portText.toInt()
        } catch (_: NumberFormatException) {
            showError(getString(R.string.error_invalid_port))
            return
        }

        if (port !in 1..65535) {
            showError(getString(R.string.error_invalid_port_range))
            return
        }

        buttonConnect.isEnabled = false
        showInfo(getString(R.string.connection_in_progress))

        thread {
            var socket: DatagramSocket? = null

            try {
                socket = DatagramSocket()
                socket.soTimeout = connectTimeoutMs

                val serverAddress = InetAddress.getByName(ip)
                val payload = "GET".toByteArray(Charsets.UTF_8)
                val sendPacket = DatagramPacket(payload, payload.size, serverAddress, port)
                socket.send(sendPacket)

                val receiveBuffer = ByteArray(4096)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(receivePacket)

                val hasResponse = receivePacket.length > 0

                runOnUiThread {
                    if (!hasResponse) {
                        showError(getString(R.string.error_server_unreachable))
                        buttonConnect.isEnabled = true
                        return@runOnUiThread
                    }

                    showSuccess(getString(R.string.connection_success))
                    buttonConnect.isEnabled = true

                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_SERVER_IP, ip)
                        putExtra(MainActivity.EXTRA_SERVER_PORT, port)
                    }
                    startActivity(intent)
                }
            } catch (_: SocketTimeoutException) {
                runOnUiThread {
                    showError(getString(R.string.error_server_unreachable))
                    buttonConnect.isEnabled = true
                }
            } catch (_: UnknownHostException) {
                runOnUiThread {
                    showError(getString(R.string.error_invalid_ip))
                    buttonConnect.isEnabled = true
                }
            } catch (_: Exception) {
                runOnUiThread {
                    showError(getString(R.string.error_server_unreachable))
                    buttonConnect.isEnabled = true
                }
            } finally {
                socket?.close()
            }
        }
    }

    private fun showError(message: String) {
        textConnectionError.text = message
        textConnectionError.setTextColor(ContextCompat.getColor(this, R.color.accent_error))
    }

    private fun showInfo(message: String) {
        textConnectionError.text = message
        textConnectionError.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun showSuccess(message: String) {
        textConnectionError.text = message
        textConnectionError.setTextColor(ContextCompat.getColor(this, R.color.accent_secondary))
    }

    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false

        return parts.all { part ->
            if (part.isEmpty() || part.length > 3) return@all false
            if (!part.all { it.isDigit() }) return@all false

            val value = part.toIntOrNull() ?: return@all false
            value in 0..255
        }
    }
}

