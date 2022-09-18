package com.example.firebot.views

import android.content.ContentValues.TAG
import android.content.DialogInterface
import android.graphics.PixelFormat
import android.net.NetworkInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.firebot.R
import com.github.pwittchen.reactivenetwork.library.rx2.Connectivity
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream


class MainActivity : AppCompatActivity(), JoystickView.JoystickListener {

    //MediaPlayer object handles the RTSP communication and RTP video streaming work
    private var _mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var libVlc: LibVLC? = null

    private val negativeButtonClick = { dialog: DialogInterface, which: Int -> }
    private var currentCamera = "RGB"
    lateinit var tvNoInternet: TextView

    var startTime: Long = 0
    var endTime: Long = 0
    var fileSize: Long = 0
    var client = OkHttpClient()

    // bandwidth in kbps
    private val goodBandwidth = 4000

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

        tvNoInternet = findViewById(R.id.tvNoInternet)

        val joystickView = findViewById<JoystickView>(R.id.joystick)
        //code to make the view transparent
        joystickView.setZOrderOnTop(true)
        val joystickViewHolder = joystickView.holder
        joystickViewHolder.setFormat(PixelFormat.TRANSPARENT)

        //observing network state
        ReactiveNetwork
            .observeNetworkConnectivity(applicationContext) //it is better to pass applicationContext
            .subscribeOn(Schedulers.io())
            //.filter(ConnectivityPredicate.hasType(ConnectivityManager.TYPE_WIFI))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { connectivity: Connectivity? ->
                if (connectivity?.state() == NetworkInfo.State.CONNECTED)
                    startVideo() //if device is connected to wifi, start the stream
                else if (connectivity?.state() == NetworkInfo.State.DISCONNECTED)
                    stopVideo()
            }

        //calling a function every second - to check internet strength
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                checkInternetStrength()
                handler.postDelayed(this, 1000)//1 sec delay
            }
        }, 0)

    }

    fun checkInternetStrength() {
        //downloading some file from the internet & calculate how long it took vs number of bytes in the file
        val request = Request.Builder()
            .url("https://publicobject.com/helloworld.txt")
            .build()

        //time before the request is made
        startTime = System.currentTimeMillis()

        client.newCall(request).enqueue(object: Callback {

            override fun onFailure(call: Call?, e: IOException?) {
                e?.printStackTrace()
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                /*val responseHeaders: Headers = response.headers()
                var i = 0
                val size: Int = responseHeaders.size()
                while (i < size) {
                    Log.d(TAG, responseHeaders.name(i).toString() + ": " + responseHeaders.value(i))
                    i++
                }*/

                val input: InputStream = response.body().byteStream()

                try {
                    val bos = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    while (input.read(buffer) !== -1) {
                        bos.write(buffer)
                    }
                    fileSize = bos.size().toLong()
                } finally {
                    input.close()
                }
                //time after request is completed
                endTime = System.currentTimeMillis()

                //calculate how long it took by subtracting end time from start time
                val timeTakenMills =
                    Math.floor((endTime - startTime).toDouble()) //time taken in milliseconds
                val timeTakenSecs = timeTakenMills / 1000 //divide by 1000 to get time in seconds
                val kilobytePerSec = Math.round(1024 / timeTakenSecs).toInt()
                if (kilobytePerSec <= goodBandwidth) {
                    // slow connection - we need to stop the robot
                    //only the original thread that created a view hierarchy can touch its views
                    runOnUiThread {
                        tvNoInternet.text = "Poor internet connection. Robot is stopped!"
                        tvNoInternet.visibility = View.VISIBLE
                    }
                } else {
                    runOnUiThread {
                        tvNoInternet.visibility = View.INVISIBLE
                    }
                }

                //get the download speed by dividing the file size by time taken to download
                val speed = fileSize / timeTakenMills
                Log.d(TAG, "Time taken in secs: $timeTakenSecs")
                Log.d(TAG, "kilobyte per sec: $kilobytePerSec")
                Log.d(TAG, "Download Speed: $speed")
                Log.d(TAG, "File size: $fileSize")
            }
        })
    }

    fun withItems(view: View) {
        val items = arrayOf("IR", "RGB", "Thermal") //items to be displayed in a list
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

    fun startVideo() {
        tvNoInternet.visibility = View.INVISIBLE
        libVlc = LibVLC(this);
        _mediaPlayer = MediaPlayer(libVlc)
        videoLayout = findViewById(R.id.videoLayout);

        _mediaPlayer!!.attachViews(videoLayout!!, null, false, false)

        val media = Media(libVlc, Uri.parse("rtsp://192.168.119.158:8554/mystream"))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=600")

        _mediaPlayer!!.setMedia(media)
        media.release()
        _mediaPlayer!!.play()
    }

    fun stopVideo() {
        tvNoInternet.text = "No internet connection!"
        tvNoInternet.visibility = View.VISIBLE
        if (_mediaPlayer != null) {
            _mediaPlayer!!.stop()
            _mediaPlayer!!.detachViews()
            _mediaPlayer!!.release()
            libVlc!!.release()
            libVlc = null
            _mediaPlayer = null
        }
    }

    override fun onJoystickMoved(xPercent: Float, yPercent: Float, id: Int) {
        Log.d("Main aktivnost", "$xPercent, $yPercent")
    }

    override fun onStop() {
        super.onStop()
        _mediaPlayer!!.stop()
        _mediaPlayer!!.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        _mediaPlayer!!.release()
        libVlc!!.release()
    }
}