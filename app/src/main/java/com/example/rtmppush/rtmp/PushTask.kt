package com.example.rtmppush.rtmp

import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * author: 80342867
 * created on: 2022/5/12 012 9:01
 * description:
 */
class PushTask private constructor() {


    fun execute(runnable: Runnable) {
        Log.i(TAG, "execute: ${THREAD_POOL_EXECUTOR.isShutdown}")
        THREAD_POOL_EXECUTOR.execute(runnable)
    }

    companion object {
        private val TAG: String = "PushTask"
        val CPU_COUNT: Int = Runtime.getRuntime().availableProcessors()
        val CORE_POOL_SIZE = 2.coerceAtLeast((CPU_COUNT - 1).coerceAtMost(4))
        val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
        val KEEP_ALIVE_SECONDS = 30L
        val sPoolWorkQueue = LinkedBlockingQueue<Runnable>(5)
        private var THREAD_POOL_EXECUTOR: ThreadPoolExecutor

        @Volatile
        private var instance: PushTask? = null

        init {
            Log.i(TAG, "$CPU_COUNT, $CORE_POOL_SIZE, $MAXIMUM_POOL_SIZE, ${sPoolWorkQueue.size}")
            THREAD_POOL_EXECUTOR = ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, sPoolWorkQueue, ThreadFactory {
                    val thread = Thread(it)
                    thread.name = "rtmp_push_thread"
                    thread
                }
            )
        }

        fun getInstance(): PushTask {
            if (instance == null) {
                synchronized(PushTask::class.java) {
                    if (instance == null) {
                        instance = PushTask()
                    }
                }
            }

            return instance!!
        }
    }
}