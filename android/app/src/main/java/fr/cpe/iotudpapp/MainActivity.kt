package fr.cpe.iotudpapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var editIp: EditText
    private lateinit var editPort: EditText
    private lateinit var editCommand: EditText
    private lateinit var buttonGet: Button
    private lateinit var buttonSendCommand: Button
    private lateinit var textResponse: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editIp = findViewById(R.id.editIp)
        editPort = findViewById(R.id.editPort)
        editCommand = findViewById(R.id.editCommand)
        buttonGet = findViewById(R.id.buttonGet)
        buttonSendCommand = findViewById(R.id.buttonSendCommand)
        textResponse = findViewById(R.id.textResponse)

        buttonGet.setOnClickListener {
            sendUdpMessage("GET")
        }

        buttonSendCommand.setOnClickListener {
            val command = editCommand.text.toString().trim().uppercase()
            if (command.isNotEmpty()) {
                sendUdpMessage(command)
            } else {
                textResponse.text = "Commande vide"
            }
        }
    }

    private fun sendUdpMessage(message: String) {
        val ip = editIp.text.toString().trim()
        val portText = editPort.text.toString().trim()

        if (ip.isEmpty()) {
            textResponse.text = "IP vide"
            return
        }

        if (portText.isEmpty()) {
            textResponse.text = "Port vide"
            return
        }

        val port = try {
            portText.toInt()
        } catch (e: NumberFormatException) {
            textResponse.text = "Port invalide"
            return
        }

        textResponse.text = "Envoi de : $message ..."

        thread {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.soTimeout = 3000

                val serverAddress = InetAddress.getByName(ip)
                val sendData = message.toByteArray(Charsets.UTF_8)

                val sendPacket = DatagramPacket(
                    sendData,
                    sendData.size,
                    serverAddress,
                    port
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
                    textResponse.text = response
                }

            } catch (e: Exception) {
                runOnUiThread {
                    textResponse.text = "Erreur : ${e.message}"
                }
            } finally {
                socket?.close()
            }
        }
    }
}