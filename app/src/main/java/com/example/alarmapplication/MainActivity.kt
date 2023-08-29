package com.example.alarmapplication
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var btnTrigger: Button
    private lateinit var btnStop: Button
    private var sendMessageTask: SendMessageTask? = null
    private var deactivateMessageTask: DeactivateMessageTask? = null
    private lateinit var statusText: TextView
    @RequiresApi(Build.VERSION_CODES.O)
    val current = LocalDateTime.now()

    val serverHost = "192.168.68.101"
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
        tvServerStatus = findViewById(R.id.tvServerStatus)
        tvAlarmStatus = findViewById(R.id.tvAlarmStatus)

        updateServerStatus("Unknown")
        updateAlarmStatus("Unknown")

        setTriggereButton(false)
        startServerStatusCheck()

        btnTrigger.setOnClickListener {
            sendMessageTask = SendMessageTask()
            sendMessageTask?.execute()
            btnStop.isEnabled = false
            logger("Sending Activate command...\n")
            btnTrigger.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
            Handler(Looper.getMainLooper()).postDelayed( Runnable(){
                @Override
                btnStop.isEnabled = true
            },5000)
        }

        btnStop.setOnClickListener {
            deactivateMessageTask = DeactivateMessageTask()
            deactivateMessageTask?.execute()
            logger("Sending Deactivate command...\n") //statusText.append("$formatted\nSending Deactivate command...\n")
            btnStop.visibility = View.GONE
            btnTrigger.visibility = View.VISIBLE
            btnTrigger.isEnabled = false
            Handler(Looper.getMainLooper()).postDelayed( Runnable(){
                @Override
                btnTrigger.isEnabled = true
            },3000)
        }
    }

    private fun setTriggereButton(status: Boolean){
        runOnUiThread {
            btnTrigger.isEnabled = status
        }
    }

    private suspend fun hasConnection() {
        withContext(Dispatchers.IO) {
            //var address: InetAddress = InetAddress.getByName(serverHost)
            //val timeout = 1500
            try {
                //val inetAddress = InetAddress.getByName(serverHost)
                //logger("inetAddress: $inetAddress")
                //val reachable = inetAddress.isReachable(3000) // Timeout in milliseconds
                //logger("$reachable")
                val startTime = System.currentTimeMillis()
                val socket = Socket()
                socket.connect(InetSocketAddress(serverHost, serverPort), 3000) // Timeout in milliseconds
                socket.close()
                val endTime = System.currentTimeMillis()
               //
                updateServerStatus("Server Up")
                val responseTime = endTime - startTime
                logger("Ping: " + responseTime.toString())
                setTriggereButton(true)

            } catch (e: Exception){
                logger(e.toString())
                updateServerStatus("Server Down")
                setTriggereButton(false)
            }

            /*if (address.isReachable(timeout)){
                logger("Server Reached!")
                updateServerStatus("Server up!")
            }else{
                logger("Server unreachable!")
                updateServerStatus("Server down!")
            }

            */
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun logger(message: String){
        runOnUiThread {
            statusText.append("$formatted\n" + message + "\n")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startServerStatusCheck() {
        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                lifecycleScope.launch {
                    logger("Checking server status...")// Stuff that updates the UI
                    hasConnection()
                }
                delay(serverStatusCheckInterval)
            }
        }
    }

    /*@RequiresApi(Build.VERSION_CODES.O)
    private fun checkServerStatus() {
        try {
            logger("Establishing connection to $serverHost on port $serverPort...")
            val inetAddress = InetAddress.getByName(serverHost)
            val reachable = inetAddress.isReachable(3000) // Timeout in milliseconds
            logger("Checking if server is reachable...")
            val serverStatus = if (reachable) "Server Up" else "Server Down"
            updateServerStatus(serverStatus)
        } catch (e: UnknownHostException) {
            e.printStackTrace()
            updateServerStatus("Server Down")
        } catch (e: IOException) {
            e.printStackTrace()
            updateServerStatus("Server Down")
        } catch (e: Exception){
            logger(e.toString())
            updateServerStatus("Server Down")
        } finally {
            updateServerStatus("Server Down")
        }

    }
    */

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

    private inner class DeactivateMessageTask : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg params: Void?): Void? {
            val serverHost = "192.168.68.101"
            val serverPort = 49152
            //statusText.append("Sending command to " + serverHost + "on port " + serverPort + "\n")
            try {
                val socket = Socket(serverHost, serverPort)
                val outputStream: OutputStream = socket.getOutputStream()

                val message = "DEACTIVATE_ALARM"
                val messageBytes = message.toByteArray()
                outputStream.write(messageBytes)

                outputStream.close()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
                logger(e.toString())
            }
            return null
        }
    }

    private inner class SendMessageTask : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg params: Void?): Void? {
            try {
                val socket = Socket(serverHost, serverPort)
                val outputStream: OutputStream = socket.getOutputStream()

                val message = "ACTIVATE_ALARM"
                val messageBytes = message.toByteArray()
                outputStream.write(messageBytes)

                outputStream.close()
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
                logger(e.toString())
            }
            return null
        }
    }
}
