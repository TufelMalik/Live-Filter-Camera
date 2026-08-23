package com.techquantum.livefiltercamera.lut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object LutLoader {
    private const val TAG = "LutLoader"
    private const val TARGET_GRID_SIZE = 64 // 64x64x64 colors -> 512x512 bitmap (8x8 tiles of 64x64)
    private const val BITMAP_SIZE = 512

    private val cache = ConcurrentHashMap<String, Bitmap>()

    @Volatile
    private var isPreloaded = false

    /**
     * Returns a cached LUT bitmap instantly if already preloaded.
     * Falls back to async loading if the cache misses (should not happen after preloadAll).
     */
    fun getCachedLutBitmap(assetPath: String): Bitmap? {
        return cache[assetPath]
    }

    /**
     * Async load for a single LUT (with caching). Used as fallback only.
     */
    suspend fun loadLutBitmap(context: Context, assetPath: String): Bitmap? = withContext(Dispatchers.Default) {
        cache[assetPath]?.let { return@withContext it }

        try {
            val cubeData = parseCubeFile(context, assetPath) ?: return@withContext null
            val bitmap = convertCubeTo512Bitmap(cubeData)
            cache[assetPath] = bitmap
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LUT from $assetPath", e)
            null
        }
    }

    /**
     * Eagerly preloads ALL LUTs in parallel at app startup.
     * After this completes, every filter click is an instant cache hit with zero latency.
     */
    suspend fun preloadAll(context: Context, assetPaths: List<String>) {
        if (isPreloaded) return

        withContext(Dispatchers.Default) {
            coroutineScope {
                val jobs = assetPaths.map { path ->
                    async {
                        if (!cache.containsKey(path)) {
                            try {
                                val cubeData = parseCubeFile(context, path)
                                if (cubeData != null) {
                                    cache[path] = convertCubeTo512Bitmap(cubeData)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Preload failed for $path: ${e.message}")
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }
        }
        isPreloaded = true
        Log.d(TAG, "Preloaded ${cache.size} LUTs into cache")
    }

    fun isAllPreloaded(): Boolean = isPreloaded

    private class CubeData(
        val size: Int,
        val rTable: FloatArray,
        val gTable: FloatArray,
        val bTable: FloatArray
    ) {
        fun sample(rNorm: Float, gNorm: Float, bNorm: Float): FloatArray {
            val rIdx = (rNorm.coerceIn(0f, 1f) * (size - 1))
            val gIdx = (gNorm.coerceIn(0f, 1f) * (size - 1))
            val bIdx = (bNorm.coerceIn(0f, 1f) * (size - 1))

            val r0 = rIdx.toInt().coerceIn(0, size - 1)
            val r1 = (r0 + 1).coerceIn(0, size - 1)
            val g0 = gIdx.toInt().coerceIn(0, size - 1)
            val g1 = (g0 + 1).coerceIn(0, size - 1)
            val b0 = bIdx.toInt().coerceIn(0, size - 1)
            val b1 = (b0 + 1).coerceIn(0, size - 1)

            val rRatio = rIdx - r0
            val gRatio = gIdx - g0
            val bRatio = bIdx - b0

            fun getIndex(r: Int, g: Int, b: Int): Int {
                return (b * size * size) + (g * size) + r
            }

            // Trilinear interpolation for each channel
            fun trilinear(table: FloatArray): Float {
                val c000 = table[getIndex(r0, g0, b0)]
                val c100 = table[getIndex(r1, g0, b0)]
                val c010 = table[getIndex(r0, g1, b0)]
                val c110 = table[getIndex(r1, g1, b0)]
                val c001 = table[getIndex(r0, g0, b1)]
                val c101 = table[getIndex(r1, g0, b1)]
                val c011 = table[getIndex(r0, g1, b1)]
                val c111 = table[getIndex(r1, g1, b1)]

                val c00 = c000 * (1f - rRatio) + c100 * rRatio
                val c10 = c010 * (1f - rRatio) + c110 * rRatio
                val c01 = c001 * (1f - rRatio) + c101 * rRatio
                val c11 = c011 * (1f - rRatio) + c111 * rRatio

                val c0 = c00 * (1f - gRatio) + c10 * gRatio
                val c1 = c01 * (1f - gRatio) + c11 * gRatio

                return c0 * (1f - bRatio) + c1 * bRatio
            }

            return floatArrayOf(
                trilinear(rTable),
                trilinear(gTable),
                trilinear(bTable)
            )
        }
    }

    private fun parseCubeFile(context: Context, assetPath: String): CubeData? {
        val inputStream = context.assets.open(assetPath)
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))

        var lutSize = 0
        var totalEntries = 0
        var rArray: FloatArray? = null
        var gArray: FloatArray? = null
        var bArray: FloatArray? = null
        var currentIndex = 0

        reader.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue

                if (line.startsWith("LUT_3D_SIZE")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        lutSize = parts[1].toInt()
                        totalEntries = lutSize * lutSize * lutSize
                        rArray = FloatArray(totalEntries)
                        gArray = FloatArray(totalEntries)
                        bArray = FloatArray(totalEntries)
                    }
                    continue
                }

                if (line.startsWith("TITLE") || line.startsWith("DOMAIN_")) {
                    continue
                }

                if (lutSize > 0 && currentIndex < totalEntries) {
                    val rgb = line.split("\\s+".toRegex())
                    if (rgb.size >= 3) {
                        rArray!![currentIndex] = rgb[0].toFloatOrNull() ?: 0f
                        gArray!![currentIndex] = rgb[1].toFloatOrNull() ?: 0f
                        bArray!![currentIndex] = rgb[2].toFloatOrNull() ?: 0f
                        currentIndex++
                    }
                }
            }
        }

        if (lutSize == 0 || rArray == null || currentIndex != totalEntries) {
            Log.e(TAG, "Invalid or incomplete .cube file: $assetPath (read $currentIndex / $totalEntries)")
            return null
        }

        return CubeData(lutSize, rArray!!, gArray!!, bArray!!)
    }

    private fun convertCubeTo512Bitmap(cubeData: CubeData): Bitmap {
        val pixels = IntArray(BITMAP_SIZE * BITMAP_SIZE)

        for (b in 0 until TARGET_GRID_SIZE) {
            val bNorm = b / (TARGET_GRID_SIZE - 1f)
            val tileX = (b % 8) * TARGET_GRID_SIZE
            val tileY = (b / 8) * TARGET_GRID_SIZE

            for (g in 0 until TARGET_GRID_SIZE) {
                val gNorm = g / (TARGET_GRID_SIZE - 1f)
                val pixelY = tileY + g

                for (r in 0 until TARGET_GRID_SIZE) {
                    val rNorm = r / (TARGET_GRID_SIZE - 1f)
                    val pixelX = tileX + r

                    val sampled = cubeData.sample(rNorm, gNorm, bNorm)
                    val rInt = (sampled[0].coerceIn(0f, 1f) * 255f).toInt()
                    val gInt = (sampled[1].coerceIn(0f, 1f) * 255f).toInt()
                    val bInt = (sampled[2].coerceIn(0f, 1f) * 255f).toInt()

                    pixels[pixelY * BITMAP_SIZE + pixelX] = Color.argb(255, rInt, gInt, bInt)
                }
            }
        }

        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, BITMAP_SIZE, 0, 0, BITMAP_SIZE, BITMAP_SIZE)
        return bitmap
    }
}
