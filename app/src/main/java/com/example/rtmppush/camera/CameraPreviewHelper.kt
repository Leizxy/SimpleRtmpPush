package com.example.rtmppush.camera

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * author: 80342867
 * created on: 2022/5/13 013 10:23
 * description: camera preview
 */
open class CameraPreviewHelper(
    private val mContext: AppCompatActivity,
    private val mPreviewView: PreviewView,
    private var mLensFacing: Int = CameraSelector.LENS_FACING_BACK
) {
    private var mCameraProvider: ProcessCameraProvider? = null

    init {
        ProcessCameraProvider.getInstance(mContext).apply {
            addListener({
                    mCameraProvider = get()
                    mLensFacing = when {
                        hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                        hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                        else -> throw IllegalStateException("Back and front camera are unavailable")
                    }
                    bindCameraUseCases()
                }, ContextCompat.getMainExecutor(mContext))

        }
    }

    private fun bindCameraUseCases() {
        val cameraSelector = CameraSelector.Builder().requireLensFacing(mLensFacing).build()

        try {
            mCameraProvider?.run {
                unbindAll()
                bindToLifecycle(mContext, cameraSelector, *getUseCases())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected open fun getUseCases(): Array<UseCase> {
        val preview =
            Preview.Builder()
                .build().also {
                    it.setSurfaceProvider(mPreviewView.surfaceProvider)
                }

        return arrayOf(preview)
    }

    private fun hasFrontCamera(): Boolean {
        return mCameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun hasBackCamera(): Boolean {
        return mCameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }
}