package com.techquantum.livefiltercamera.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoRecordManager(
    private val context: Context
) {
    private val TAG = "VideoRecordManager"
    private var activeRecording: Recording? = null
    private var isRecording = false

    private val maxDurationSeconds = 60
    private var durationHandler = Handler(Looper.getMainLooper())
    private var durationRunnable: Runnable? = null
    private var elapsedSeconds = 0

    val recorder: Recorder = Recorder.Builder()
        .setQualitySelector(
            QualitySelector.from(
                Quality.HD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
            )
        )
        .build()

    val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    fun startRecording(
        onRecordingStarted: () -> Unit,
        onDurationTick: (Int) -> Unit,
        onRecordingFinished: (Uri, Bitmap?) -> Unit,
        onError: (String) -> Unit
    ) {
        if (isRecording || activeRecording != null) {
            Log.w(TAG, "Recording is already active")
            return
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "VID_LFC_$timeStamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + File.separator + "LiveFilterCamera")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()

        val pendingRecording = videoCapture.output
            .prepareRecording(context, outputOptions)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled()
        }

        try {
            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        elapsedSeconds = 0
                        onRecordingStarted()
                        startTimer(onDurationTick) {
                            stopRecording()
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        stopTimer()
                        isRecording = false
                        activeRecording = null

                        if (!event.hasError()) {
                            val outputUri = event.outputResults.outputUri
                            val thumbnail = generateThumbnail(outputUri)
                            onRecordingFinished(outputUri, thumbnail)
                        } else {
                            Log.e(TAG, "Video recording error: ${event.error}")
                            onError("Recording error: ${event.error}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onError(e.message ?: "Failed to start recording")
        }
    }

    fun stopRecording() {
        if (!isRecording && activeRecording == null) return
        stopTimer()
        try {
            activeRecording?.stop()
            activeRecording = null
            isRecording = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }

    fun isRecordingActive(): Boolean = isRecording

    private fun startTimer(onTick: (Int) -> Unit, onMaxReached: () -> Unit) {
        durationRunnable = object : Runnable {
            override fun run() {
                elapsedSeconds++
                onTick(elapsedSeconds)
                if (elapsedSeconds >= maxDurationSeconds) {
                    onMaxReached()
                } else {
                    durationHandler.postDelayed(this, 1000)
                }
            }
        }
        durationHandler.postDelayed(durationRunnable!!, 1000)
    }

    private fun stopTimer() {
        durationRunnable?.let { durationHandler.removeCallbacks(it) }
        durationRunnable = null
    }

    private fun generateThumbnail(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(180, 180), null)
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val bmp = retriever.frameAtTime
                retriever.release()
                bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract video thumbnail", e)
            null
        }
    }
}
