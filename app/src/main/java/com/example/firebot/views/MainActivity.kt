package com.example.firebot.views

import android.content.DialogInterface
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.firebot.R
import com.github.pwittchen.reactivenetwork.library.rx2.Connectivity
import com.github.pwittchen.reactivenetwork.library.rx2.ConnectivityPredicate
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout


class MainActivity : AppCompatActivity(), JoystickView.JoystickListener {

    //MediaPlayer object handles the RTSP communication and RTP video streaming work
    private var _mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var libVlc: LibVLC? = null

    val negativeButtonClick = { dialog: DialogInterface, which: Int -> }
    var currentCamera = "RGB"
    lateinit var tvInternet: TextView

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

        tvInternet = findViewById<TextView>(R.id.tvInternet)

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
                else
                    stopVideo()
                Log.d("Main aktivnost", "${connectivity?.state()}")
            }

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
        tvInternet.visibility = View.INVISIBLE
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
        tvInternet.visibility = View.VISIBLE
        _mediaPlayer!!.stop()
        _mediaPlayer!!.detachViews()
        _mediaPlayer!!.release()
        libVlc!!.release()
        libVlc = null
        _mediaPlayer = null
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