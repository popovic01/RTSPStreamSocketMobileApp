package com.example.firebot.views

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64.*
import android.util.Log
import android.view.*
import com.example.firebot.R
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*

class MainActivity : AppCompatActivity(), MediaPlayer.OnPreparedListener, SurfaceHolder.Callback {

    //activity knows when the rendering surface is ready for use
    //MediaPlayer object handles the RTSP communication and RTP video streaming work
    private var _mediaPlayer: MediaPlayer? = null
    private lateinit var _surfaceHolder: SurfaceHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Set up a full-screen black window
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val window: Window = window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setBackgroundDrawableResource(R.color.black)
        setContentView(R.layout.activity_main)
        Log.d("Main aktivnost", "Bla")

        //Configure the view that renders live video
        val surfaceView = findViewById<View>(R.id.surfaceView) as SurfaceView
        _surfaceHolder = surfaceView.holder
        _surfaceHolder.addCallback(this)
        _surfaceHolder.setFixedSize(320, 240)
    }

    override fun onPrepared(p0: MediaPlayer?) {
        _mediaPlayer?.start()
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        _mediaPlayer = MediaPlayer()
        _mediaPlayer!!.setDisplay(_surfaceHolder)
        val context: Context = applicationContext
        val headers: Map<String, String> = getRtspHeaders()
        val source: Uri = Uri.parse(RTSP_URL)
        try {
            //Specify the IP camera's URL and auth headers
            _mediaPlayer!!.setDataSource(context, source, headers)

            //Begin the process of setting up a video stream
            _mediaPlayer!!.setOnPreparedListener(this)
            _mediaPlayer!!.prepareAsync()
        } catch (e: Exception) {
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {

    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        _mediaPlayer?.release()
    }

    //helper method to get the headers needed to communicate with the RTSP server
    private fun getRtspHeaders(): Map<String, String> {
        val headers: MutableMap<String, String> = HashMap<String, String>()
        val basicAuthValue = getBasicAuthValue(USERNAME, PASSWORD)
        headers.put("Authorization", basicAuthValue)
        return headers
    }

    private fun getBasicAuthValue(usr: String, pwd: String): String {
        val credentials = "$usr:$pwd"
        val flags: Int = URL_SAFE or NO_WRAP
        val bytes: ByteArray = credentials.toByteArray()
        return "Basic " + encodeToString(bytes, flags)
    }

    fun String.toByteArray(
        charset: Charset = UTF_8
    ): ByteArray = (this as java.lang.String).getBytes(charset)

    companion object {
        const val USERNAME = "admin"
        const val PASSWORD = "camera"
        const val RTSP_URL = "rtsp://rtsp.stream/pattern"
        //RTSP_URL needs to be updated to camera's local IP address!!
    }
}