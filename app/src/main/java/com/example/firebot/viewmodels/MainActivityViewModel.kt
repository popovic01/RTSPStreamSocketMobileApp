package com.example.firebot.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivityViewModel : ViewModel() {

    //for calculating internet strength
    var startTime: Long = 0
    var endTime: Long = 0
    var fileSize: Long = 0
    private var client = OkHttpClient()

    // bandwidth in kbps
    private val goodBandwidth = 4000

    private val TAG: String = "ViewModel MainActivity"

    private val _slowInternetSpeed = MutableLiveData<Boolean>()
    fun slowInternetSpeed() : LiveData<Boolean> {
        //function for public purpose - it is public
        return _slowInternetSpeed
    }

    fun checkInternetSpeed() {
        //downloading some file from the internet & calculate how long it took vs number of bytes in the file
        val request = Request.Builder()
            .url("https://publicobject.com/helloworld.txt")
            .build()

        //time before the request is made
        startTime = System.currentTimeMillis()

        //asynchronous request - making a network call from a background thread
        client.newCall(request).enqueue(object: Callback {

            override fun onFailure(call: Call?, e: IOException?) {
                e?.printStackTrace()
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

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
                    _slowInternetSpeed.postValue(true)
                } else
                    _slowInternetSpeed.postValue(false)
                //get the download speed by dividing the file size by time taken to download
                val speed = fileSize / timeTakenMills
                Log.d(TAG, "Time taken in secs: $timeTakenSecs")
                Log.d(TAG, "kilobyte per sec: $kilobytePerSec")
                Log.d(TAG, "Download Speed: $speed")
                Log.d(TAG, "File size: $fileSize")
            }
        })
    }

}