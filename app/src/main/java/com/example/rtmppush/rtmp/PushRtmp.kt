package com.example.rtmppush.rtmp

/**
 * author: 80342867
 * created on: 2022/5/13 013 10:55
 * description:
 */
class PushRtmp {
    companion object {
        @Volatile
        private var instance: PushRtmp? = null

        init {
            System.loadLibrary("native-lib")
        }

        fun getInstance(): PushRtmp {
            if (instance == null) {
                synchronized(PushRtmp::class.java) {
                    if (instance == null) {
                        instance = PushRtmp()
                    }
                }
            }

            return instance!!
        }
    }

    fun sendVideoData(data: ByteArray, len: Int, tms: Long):Boolean {
        return sendData(data, len, tms)
    }

    fun connectServer(url: String):Boolean {
        return connect(url)
    }

    private external fun sendData(data: ByteArray, len: Int, tms: Long): Boolean

    private external fun connect(url: String): Boolean
}