package com.example.alarmapplication
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.MovementMethod
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.expandVertically
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class MainActivity : AppCompatActivity() {

    private lateinit var btnTrigger: Button
    private lateinit var btnStop: Button
    private lateinit var scrollText: ScrollView
    private var sendMessageTask: SendMessageTask? = null
    private lateinit var statusText: TextView
    @RequiresApi(Build.VERSION_CODES.O)
    val current = LocalDateTime.now()

    val serverHost = "192.168.68.103"
    val serverPort = 49152

    @RequiresApi(Build.VERSION_CODES.O)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    @RequiresApi(Build.VERSION_CODES.O)
    val formatted = current.format(formatter)

    private val serverStatusCheckInterval = 10000L // 5 seconds
    private lateinit var tvServerStatus: TextView
    private lateinit var tvAlarmStatus: TextView

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTrigger = findViewById(R.id.btnTrigger)
        btnStop = findViewById(R.id.btnStop)
        statusText = findViewById(R.id.statusText)
        scrollText = findViewById(R.id.sText)
        tvServerStatus = findViewById(R.id.tvServerStatus)
        tvAlarmStatus = findViewById(R.id.tvAlarmStatus)

        updateServerStatus("Unknown")
        updateAlarmStatus("Unknown")

        startServerStatusCheck()

        btnTrigger.setOnClickListener {
            sendMessageTask = SendMessageTask("ACTIVATE_ALARM")
            sendMessageTask?.execute()
            logger("Sending Activate command...\n")
            initialize_button(false)
        }

        btnStop.setOnClickListener {
            sendMessageTask = SendMessageTask("DEACTIVATE_ALARM")
            sendMessageTask?.execute()
            logger("Sending Deactivate command...\n") //statusText.append("$formatted\nSending Deactivate command...\n")
            initialize_button(true)
        }
    }

    private fun initialize_button(flag: Boolean){
        var flag = flag
        runOnUiThread {
            if (flag){
                btnStop.visibility = View.GONE
                btnTrigger.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed( Runnable {
                    @Override
                    btnTrigger.isEnabled = flag
                },300)
            }else{
                btnStop.visibility = View.VISIBLE
                btnTrigger.visibility = View.GONE
                Handler(Looper.getMainLooper()).postDelayed( Runnable {
                    @Override
                    btnStop.isEnabled = flag.not()
                },300)
            }

        }
    }


    private fun updateServerStatus(status: String) {
        runOnUiThread {
            tvServerStatus.text = "Server Status: $status"
        }
    }

    private fun updateAlarmStatus(status: String) {
        runOnUiThread {
            tvAlarmStatus.text = "Alarm Status: $status"
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun logger(message: String){
        runOnUiThread {
            statusText.append("$formatted\n" + message + "\n")
            scrollText.fullScroll(View.FOCUS_DOWN)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startServerStatusCheck() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                lifecycleScope.launch {
                    logger("Checking server status...")// Stuff that updates the UI

                    // Replace "YourCommandHere" with the actual command you want to send
                    val sendMessageTask = SendMessageTask("Check Status")
                    val serverReply = sendMessageTask.execute()

                    // Wait for the task to complete and access the server reply
                    //val serverReply = sendMessageTask.getServerReply()

                    if (serverReply != null) {
                        logger("Server's reply on startserver: $serverReply")
                        // Process the server reply as needed

                    } else {
                        logger("Failed to receive server's reply.")
                    }

                    //hasConnection()
                }
                delay(serverStatusCheckInterval)
            }
        }
    }

    private inner class SendMessageTask(command: String) : AsyncTask<Void, Void, String>() {
        val message = command
        private var serverReply: String? = null
        override fun doInBackground(vararg params: Void?): String? {
            var reply: String? = null
            try {
                Socket(serverHost, serverPort).use { socket ->
                    val outputStream: OutputStream = socket.getOutputStream()
                    val messageBytes = message.toByteArray()
                    outputStream.write(messageBytes)

                    val inputStream: InputStream = socket.getInputStream()
                    val replyBytes = ByteArray(1024)
                    val bytesRead = inputStream.read(replyBytes)

                    if (bytesRead > 0) {
                        serverReply = String(replyBytes, 0, bytesRead)
                        logger("On background"+serverReply.toString())
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logger(e.toString())
            }
            logger("On background before return"+serverReply.toString())
            return serverReply
        }
        fun getServerReply(): String? {
            return serverReply
        }
        override fun onPostExecute(result: String?) {
            // Handle the server's reply here
            try{
                if (result != null) {
                    // Process the reply
                    logger("onPOSTExecute: Server's reply: $result")
                    updateServerStatus("Server Up")
                    updateAlarmStatus(result)
                    if(result == "Alarm Deactivated"){
                        initialize_button(true)
                    }else if (result == "Alarm Activated"){
                        initialize_button(false)
                    }

                } else {
                    logger("onPOSTExecute: Failed to receive server's reply.")
                    updateServerStatus("Server Down")
                    btnTrigger.isEnabled = false
                    btnStop.isEnabled = false
                }
            }catch (e: Exception) {
                e.printStackTrace()
                updateServerStatus("Server Down")
                logger(e.toString())
            }
        }
    }
}