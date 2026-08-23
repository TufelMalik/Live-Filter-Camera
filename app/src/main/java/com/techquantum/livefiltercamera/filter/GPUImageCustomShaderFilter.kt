package com.techquantum.livefiltercamera.filter

import android.content.Context
import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import java.io.BufferedReader
import java.io.InputStreamReader

class GPUImageCustomShaderFilter(
    fragmentShaderSource: String,
    private var currentIntensity: Float = 0.5f
) : GPUImageFilter(NO_FILTER_VERTEX_SHADER, fragmentShaderSource) {

    private var intensityLocation = -1
    private var timeLocation = -1
    private var startTimeMs = System.currentTimeMillis()

    override fun onInit() {
        super.onInit()
        intensityLocation = GLES20.glGetUniformLocation(program, "intensity")
        timeLocation = GLES20.glGetUniformLocation(program, "time")
    }

    override fun onInitialized() {
        super.onInitialized()
        setIntensity(currentIntensity)
        updateTime()
    }

    fun setIntensity(intensity: Float) {
        currentIntensity = intensity.coerceIn(0f, 1f)
        if (isInitialized && intensityLocation != -1) {
            setFloat(intensityLocation, currentIntensity)
        }
    }

    fun getIntensity(): Float = currentIntensity

    fun updateTime() {
        if (isInitialized && timeLocation != -1) {
            val elapsedSec = (System.currentTimeMillis() - startTimeMs) / 1000.0f
            setFloat(timeLocation, elapsedSec)
        }
    }

    override fun onDraw(
        textureId: Int,
        vertexBuffer: java.nio.FloatBuffer,
        textureBuffer: java.nio.FloatBuffer
    ) {
        updateTime()
        super.onDraw(textureId, vertexBuffer, textureBuffer)
    }

    companion object {
        fun fromAsset(context: Context, assetPath: String, initialIntensity: Float = 0.5f): GPUImageCustomShaderFilter {
            val source = context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream)).readText()
            }
            return GPUImageCustomShaderFilter(source, initialIntensity)
        }
    }
}
