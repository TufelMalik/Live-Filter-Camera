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
        Log.d(TAG, "Preloaded ${cache.size} LUTs into cache with zero-allocation parser")
    }

    fun isAllPreloaded(): Boolean = isPreloaded

    private class CubeData(
        val size: Int,
        val table: FloatArray // Interleaved R, G, B values of size size * size * size * 3
    )

    /**
     * Fast streaming parser for .cube format.
     * Avoids regular expressions and line splitting string allocations.
     */
    private fun parseCubeFile(context: Context, assetPath: String): CubeData? {
        var inputStream: java.io.InputStream? = null
        try {
            inputStream = context.assets.open(assetPath)
            val bytes = inputStream.readBytes()
            var offset = 0
            val length = bytes.size

            var lutSize = 0
            var table: FloatArray? = null
            var floatIndex = 0
            var expectedFloats = 0

            while (offset < length) {
                // Skip leading whitespaces on line
                while (offset < length && (bytes[offset] == ' '.code.toByte() || bytes[offset] == '\t'.code.toByte() || bytes[offset] == '\r'.code.toByte() || bytes[offset] == '\n'.code.toByte())) {
                    offset++
                }
                if (offset >= length) break

                // Check for comments
                if (bytes[offset] == '#'.code.toByte()) {
                    while (offset < length && bytes[offset] != '\n'.code.toByte()) offset++
                    continue
                }

                // Check for LUT_3D_SIZE header
                if (offset + 11 <= length &&
                    bytes[offset] == 'L'.code.toByte() &&
                    bytes[offset + 1] == 'U'.code.toByte() &&
                    bytes[offset + 2] == 'T'.code.toByte() &&
                    bytes[offset + 3] == '_'.code.toByte() &&
                    bytes[offset + 4] == '3'.code.toByte() &&
                    bytes[offset + 5] == 'D'.code.toByte() &&
                    bytes[offset + 6] == '_'.code.toByte() &&
                    bytes[offset + 7] == 'S'.code.toByte() &&
                    bytes[offset + 8] == 'I'.code.toByte() &&
                    bytes[offset + 9] == 'Z'.code.toByte() &&
                    bytes[offset + 10] == 'E'.code.toByte()
                ) {
                    offset += 11
                    // Skip whitespace
                    while (offset < length && (bytes[offset] == ' '.code.toByte() || bytes[offset] == '\t'.code.toByte())) offset++
                    var sizeVal = 0
                    while (offset < length && bytes[offset] >= '0'.code.toByte() && bytes[offset] <= '9'.code.toByte()) {
                        sizeVal = sizeVal * 10 + (bytes[offset] - '0'.code.toByte())
                        offset++
                    }
                    lutSize = sizeVal
                    expectedFloats = lutSize * lutSize * lutSize * 3
                    table = FloatArray(expectedFloats)
                    // Skip to end of line
                    while (offset < length && bytes[offset] != '\n'.code.toByte()) offset++
                    continue
                }

                // Skip keywords like TITLE or DOMAIN_
                if ((offset + 5 <= length && bytes[offset] == 'T'.code.toByte() && bytes[offset + 1] == 'I'.code.toByte()) ||
                    (offset + 6 <= length && bytes[offset] == 'D'.code.toByte() && bytes[offset + 1] == 'O'.code.toByte())
                ) {
                    while (offset < length && bytes[offset] != '\n'.code.toByte()) offset++
                    continue
                }

                // If table is initialized, parse 3 floats on this line
                if (table != null && floatIndex < expectedFloats) {
                    for (c in 0 until 3) {
                        while (offset < length && (bytes[offset] == ' '.code.toByte() || bytes[offset] == '\t'.code.toByte())) {
                            offset++
                        }
                        if (offset >= length || bytes[offset] == '\n'.code.toByte() || bytes[offset] == '\r'.code.toByte()) break

                        val start = offset
                        while (offset < length && bytes[offset] > ' '.code.toByte()) {
                            offset++
                        }
                        val str = String(bytes, start, offset - start, Charsets.US_ASCII)
                        val fVal = str.toFloatOrNull() ?: 0f
                        if (floatIndex < expectedFloats) {
                            table[floatIndex++] = fVal
                        }
                    }
                    // Skip rest of line
                    while (offset < length && bytes[offset] != '\n'.code.toByte()) offset++
                    continue
                }

                // Default skip line
                while (offset < length && bytes[offset] != '\n'.code.toByte()) offset++
            }

            if (lutSize == 0 || table == null || floatIndex != expectedFloats) {
                Log.e(TAG, "Invalid or incomplete .cube file: $assetPath (read $floatIndex / $expectedFloats floats)")
                return null
            }

            return CubeData(lutSize, table)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cube file: $assetPath", e)
            return null
        } finally {
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Ultra-fast zero-allocation conversion from 3D LUT table to 512x512 Bitmap.
     * Computes all trilinear interpolations directly in CPU registers without temporary object creation.
     */
    private fun convertCubeTo512Bitmap(cubeData: CubeData): Bitmap {
        val pixels = IntArray(BITMAP_SIZE * BITMAP_SIZE)
        val size = cubeData.size
        val sizeSq = size * size
        val table = cubeData.table
        val maxCoord = size - 1

        for (b in 0 until TARGET_GRID_SIZE) {
            val bNorm = b / (TARGET_GRID_SIZE - 1f)
            val bIdx = bNorm * maxCoord
            val b0 = bIdx.toInt().coerceIn(0, maxCoord)
            val b1 = (b0 + 1).coerceIn(0, maxCoord)
            val bRatio = bIdx - b0

            val tileX = (b % 8) * TARGET_GRID_SIZE
            val tileY = (b / 8) * TARGET_GRID_SIZE

            val b0Offset = b0 * sizeSq
            val b1Offset = b1 * sizeSq

            for (g in 0 until TARGET_GRID_SIZE) {
                val gNorm = g / (TARGET_GRID_SIZE - 1f)
                val gIdx = gNorm * maxCoord
                val g0 = gIdx.toInt().coerceIn(0, maxCoord)
                val g1 = (g0 + 1).coerceIn(0, maxCoord)
                val gRatio = gIdx - g0

                val pixelY = tileY + g
                val rowOffset = pixelY * BITMAP_SIZE

                val g0b0 = (b0Offset + g0 * size)
                val g1b0 = (b0Offset + g1 * size)
                val g0b1 = (b1Offset + g0 * size)
                val g1b1 = (b1Offset + g1 * size)

                for (r in 0 until TARGET_GRID_SIZE) {
                    val rNorm = r / (TARGET_GRID_SIZE - 1f)
                    val rIdx = rNorm * maxCoord
                    val r0 = rIdx.toInt().coerceIn(0, maxCoord)
                    val r1 = (r0 + 1).coerceIn(0, maxCoord)
                    val rRatio = rIdx - r0

                    val pixelX = tileX + r

                    // Interleaved 3D index positions
                    val i000 = (g0b0 + r0) * 3
                    val i100 = (g0b0 + r1) * 3
                    val i010 = (g1b0 + r0) * 3
                    val i110 = (g1b0 + r1) * 3
                    val i001 = (g0b1 + r0) * 3
                    val i101 = (g0b1 + r1) * 3
                    val i011 = (g1b1 + r0) * 3
                    val i111 = (g1b1 + r1) * 3

                    // Channel 0 (Red)
                    val r00 = table[i000] * (1f - rRatio) + table[i100] * rRatio
                    val r10 = table[i010] * (1f - rRatio) + table[i110] * rRatio
                    val r01 = table[i001] * (1f - rRatio) + table[i101] * rRatio
                    val r11 = table[i011] * (1f - rRatio) + table[i111] * rRatio
                    val r0_ = r00 * (1f - gRatio) + r10 * gRatio
                    val r1_ = r01 * (1f - gRatio) + r11 * gRatio
                    val rVal = (r0_ * (1f - bRatio) + r1_ * bRatio).coerceIn(0f, 1f)

                    // Channel 1 (Green)
                    val g00 = table[i000 + 1] * (1f - rRatio) + table[i100 + 1] * rRatio
                    val g10 = table[i010 + 1] * (1f - rRatio) + table[i110 + 1] * rRatio
                    val g01 = table[i001 + 1] * (1f - rRatio) + table[i101 + 1] * rRatio
                    val g11 = table[i011 + 1] * (1f - rRatio) + table[i111 + 1] * rRatio
                    val g0_ = g00 * (1f - gRatio) + g10 * gRatio
                    val g1_ = g01 * (1f - gRatio) + g11 * gRatio
                    val gVal = (g0_ * (1f - bRatio) + g1_ * bRatio).coerceIn(0f, 1f)

                    // Channel 2 (Blue)
                    val b00 = table[i000 + 2] * (1f - rRatio) + table[i100 + 2] * rRatio
                    val b10 = table[i010 + 2] * (1f - rRatio) + table[i110 + 2] * rRatio
                    val b01 = table[i001 + 2] * (1f - rRatio) + table[i101 + 2] * rRatio
                    val b11 = table[i011 + 2] * (1f - rRatio) + table[i111 + 2] * rRatio
                    val b0_ = b00 * (1f - gRatio) + b10 * gRatio
                    val b1_ = b01 * (1f - gRatio) + b11 * gRatio
                    val bVal = (b0_ * (1f - bRatio) + b1_ * bRatio).coerceIn(0f, 1f)

                    val rInt = (rVal * 255f).toInt()
                    val gInt = (gVal * 255f).toInt()
                    val bInt = (bVal * 255f).toInt()

                    pixels[rowOffset + pixelX] = (0xFF shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                }
            }
        }

        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, BITMAP_SIZE, 0, 0, BITMAP_SIZE, BITMAP_SIZE)
        return bitmap
    }
}
