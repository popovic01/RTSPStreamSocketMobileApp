package com.example.firebot.views

import android.content.Context
import android.content.DialogInterface
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Base64.*
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.firebot.R
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*

class MainActivity : AppCompatActivity(), MediaPlayer.OnPreparedListener, SurfaceHolder.Callback, JoystickView.JoystickListener,
    MediaPlayer.OnErrorListener {

    //activity knows when the rendering surface is ready for use
    //MediaPlayer object handles the RTSP communication and RTP video streaming work
    private var _mediaPlayer: MediaPlayer? = null
    private lateinit var _surfaceHolder: SurfaceHolder

    val negativeButtonClick = { dialog: DialogInterface, which: Int -> }
    var currentCamera = "1"

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

        //Configure the view that renders live video
        val surfaceView = findViewById<View>(R.id.surfaceView) as SurfaceView
        _surfaceHolder = surfaceView.holder
        _surfaceHolder.addCallback(this)
        _surfaceHolder.setFixedSize(320, 240)

        val joystickView = findViewById<JoystickView>(R.id.joystick)
        //code to make the view transparent
        joystickView.setZOrderOnTop(true)
        val joystickViewHolder = joystickView.holder
        joystickViewHolder.setFormat(PixelFormat.TRANSPARENT)
    }

    fun withItems(view: View) {
        val items = arrayOf("1", "2", "3", "4") //items to be displayed in a list
        val builder = AlertDialog.Builder(this)
        with(builder)
        {
            setTitle("List of available cameras")
            setItems(items) { dialog, which ->
                if (currentCamera == items[which])
                    Toast.makeText(applicationContext, "$currentCamera is already selected", Toast.LENGTH_SHORT).show()
                else {
                    currentCamera = items[which]
                    Toast.makeText(applicationContext,"Live stream from camera $currentCamera is on", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("Cancel", negativeButtonClick)
            setIcon(resources.getDrawable(android.R.drawable.ic_menu_camera, theme))
            show()
        }
    }

    override fun onPrepared(p0: MediaPlayer?) {
        Log.d("Main aktivnost", "Pocetak")
        _mediaPlayer?.start()
        Log.d("Main aktivnost", "Kraj")
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        _mediaPlayer = MediaPlayer()
        _mediaPlayer!!.setDisplay(_surfaceHolder)

        val context: Context = applicationContext
        val headers: Map<String, String> = getRtspHeaders()
        val source: Uri = Uri.parse(RTSP_URL)
        try {
            //Specify the IP camera's URL and auth headers
            _mediaPlayer!!.setDataSource("rtsp://192.168.32.161:8554/mystream")

            //Begin the process of setting up a video stream
            _mediaPlayer!!.setOnPreparedListener(this)
            _mediaPlayer!!.prepareAsync()
            Log.d("Main aktivnost", "$_mediaPlayer")
        } catch (e: Exception) {
            Log.d("Main aktivnost", "${e.message}")
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
        const val RTSP_URL = "rtsp://192.168.33.212:8554/mystream"
        //rtsp://localhost:8554/mystream
        //rtsp://rtsp.stream/pattern
        //RTSP_URL needs to be updated to camera's local IP address!!
    }

    override fun onJoystickMoved(xPercent: Float, yPercent: Float, id: Int) {
        Log.d("Main aktivnost", "$xPercent, $yPercent")
    }

    override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        Log.d("Main aktivnost", "ERROR state")
        return true
    }
}