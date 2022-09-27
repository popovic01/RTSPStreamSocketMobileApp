package com.example.firebot.views

import android.annotation.SuppressLint
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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.firebot.R
import com.example.firebot.viewmodels.MainActivityViewModel
import com.github.pwittchen.reactivenetwork.library.rx2.Connectivity
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.*
import kotlin.NoSuchElementException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), JoystickView.JoystickListener {

    private val TAG: String = "Main activity"

    //MediaPlayer object handles the RTSP communication and RTP video streaming work
    private var _mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var libVlc: LibVLC? = null

    //for socket communication
    private var client: Client? = null

    lateinit var tvInternet: TextView
    var disconnected: Boolean = false

    private val negativeButtonClick = { dialog: DialogInterface, which: Int -> }
    private var currentCamera = "RGB"

    @SuppressLint("CheckResult")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //set up a full-screen black window
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val window: Window = window
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.setBackgroundDrawableResource(R.color.black)
        setContentView(R.layout.activity_main)

        val viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)

        tvInternet = findViewById(R.id.tvInternet)

        Thread {
            try {
                client = Client("192.168.10.122", 7777)
            } catch (e: Exception) {
                Log.d("Konekcija", "${e.message}")
            }
            Log.d("Konekcija", "$client")
        }.start()

        val joystickView = findViewById<JoystickView>(R.id.joystick)
        //code to make the view transparent
        joystickView.setZOrderOnTop(true)
        val joystickViewHolder = joystickView.holder
        joystickViewHolder.setFormat(PixelFormat.TRANSPARENT)

        //observing network state
        ReactiveNetwork
            .observeNetworkConnectivity(applicationContext) //it is better to pass applicationContext
            .subscribeOn(Schedulers.io()) //designate worker thread (background) - where the work is going to be done
            //.filter(ConnectivityPredicate.hasType(ConnectivityManager.TYPE_WIFI))
            .observeOn(AndroidSchedulers.mainThread()) //designate observer thread (main) - where the results are going to be observed from
            .subscribe { connectivity: Connectivity? ->
                if (connectivity?.state() == NetworkInfo.State.CONNECTED) {
                    disconnected = false
                    startVideo() //if device is connected to wifi, start the stream
                } else if (connectivity?.state() == NetworkInfo.State.DISCONNECTED) {
                    disconnected = true
                    stopVideo() //if device is disconnected from wifi, stop the stream
                }
            }

        val handler = Handler(Looper.getMainLooper())
        //the Runnable will execute on the thread to which this handler is attached - main in this case
        handler.postDelayed(object : Runnable {
            override fun run() {
                //checking internet speed only if device is connected to the internet
                if (!disconnected) {
                    //calling a function every second - to check internet strength
                    viewModel.checkInternetSpeed()
                }
                handler.postDelayed(this, 1000) //1 second delay
            }
        }, 0)

        //observing changes of internet speed
        viewModel.slowInternetSpeed().observe(this@MainActivity, Observer {
            //viewModel.slowInternetSpeed().value == true = it
            if (viewModel.slowInternetSpeed().value == true) {
                runOnUiThread {
                    //if speed is low, show the text view alert
                    tvInternet.text = getString(R.string.poor_internet)
                    tvInternet.visibility = View.VISIBLE
                }
            } else {
                runOnUiThread {
                    tvInternet.visibility = View.INVISIBLE
                }
            }
        })
    }

    fun startVideo() {
        tvInternet.visibility = View.INVISIBLE
        libVlc = LibVLC(this);
        _mediaPlayer = MediaPlayer(libVlc)
        videoLayout = findViewById(R.id.videoLayout);

        _mediaPlayer!!.attachViews(videoLayout!!, null, false, false)

        val media = Media(libVlc, Uri.parse("rtsp://192.168.33.204:8554/mystream"))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=600")

        _mediaPlayer!!.setMedia(media)
        media.release()
        _mediaPlayer!!.play()
    }

    fun stopVideo() {
        tvInternet.text = getString(R.string.no_internet)
        tvInternet.visibility = View.VISIBLE
        if (_mediaPlayer != null) {
            _mediaPlayer!!.stop()
            _mediaPlayer!!.detachViews()
            _mediaPlayer!!.release()
            libVlc!!.release()
            libVlc = null
            _mediaPlayer = null
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

    override fun onJoystickMoved(xPercent: Float, yPercent: Float, id: Int) {
        Log.d(TAG, "$xPercent, $yPercent")
        val t = Thread {client?.run()}
        t.start()
        /*Thread {
            client?.run()
        }.start()*/
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

    class Client(address: String, port: Int) {
        private val connection: Socket = Socket(address, port)
        private var connected: Boolean = false

        init {
            Log.d("Konekcija","Connected to the server at $address on port $port")
        }

        private val reader: Scanner = Scanner(connection.getInputStream())
        private val writer: OutputStream = connection.getOutputStream()

        fun run() {
            //thread { run() }
            connected = true
            while (connected) {
                write("Koordinate")
                /*while (reader.hasNextLine()) {
                    read()
                }*/
                /*val input = readLine() ?: ""
                //print(input)
                if ("exit" in input) {
                    connected = false
                    reader.close()
                    connection.close()
                    println("Connected")
                } else {
                    println("Not connected")
                    write(input)
                }*/
            }
            /*connected = false
            reader.close()
            connection.close()*/
            Log.d("Konekcija","Connection closed")
        }

        private fun write(message: String) {
            writer.write((message).toByteArray(Charset.defaultCharset()))
        }

        fun close() {
            connection.close()
        }

        private fun read() {
            try {
                Log.d("Konekcija", reader.nextLine())
            } catch (e: NoSuchElementException) {
                Log.d("Konekcija","There is no next line")
            }
        }

    }
}