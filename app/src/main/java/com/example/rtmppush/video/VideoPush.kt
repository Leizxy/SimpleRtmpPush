package com.example.rtmppush.video

import android.util.Log
import com.example.rtmppush.rtmp.PushRtmp
import com.example.rtmppush.rtmp.PushTask
import com.example.rtmppush.rtmp.RTMPPackage
import java.util.concurrent.LinkedBlockingQueue

/**
 * author: 80342867
 * created on: 2022/5/16 016 14:56
 * description:
 */
class VideoPush(private val mUrl: String) : Runnable {
    private val TAG: String = "VideoPush"
    private val queue: LinkedBlockingQueue<RTMPPackage> = LinkedBlockingQueue()
    private var mbLiving: Boolean = false
    private var mSpsPps: ByteArray? = null

    fun addSpsAndPps(data: ByteArray) {
        mSpsPps = data
    }

    fun addVideoData(data: ByteArray, time: Long) {
        if (!mbLiving) return
        queue.add(RTMPPackage(data, time))
    }

    fun startPush() {
        PushTask.getInstance().execute(this)
    }

    fun stopPush() {
        mbLiving = false
    }

    override fun run() {
        if (!PushRtmp.getInstance().connectServer(mUrl)) {
            return
        }

        mbLiving = true

        mSpsPps?.let {
            queue.clear()
            addVideoData(it, 0)
        }

        while (mbLiving) {
            var rtmpPackage: RTMPPackage? = null
            try {
                rtmpPackage = queue.take()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            rtmpPackage?.apply {
                buffer?.let {
                    if (it.isNotEmpty()) {
                        PushRtmp.getInstance().sendVideoData(it, it.size, this.time)
                    }
                }
            }
        }
    }

}