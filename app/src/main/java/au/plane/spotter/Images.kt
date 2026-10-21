package au.plane.spotter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads bundled photos off the main thread, downsampled to roughly the width needed. */
object PhotoLoader {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(context: Context, asset: String, targetWidth: Int): Bitmap? {
        val key = "$asset@$targetWidth"
        cache.get(key)?.let { return it }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, opts) }
                ?.also { cache.put(key, it) }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun PlanePhoto(
    plane: Plane,
    modifier: Modifier = Modifier,
    targetWidth: Int = 512,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val asset = plane.photo?.asset
    val bitmap by produceState<ImageBitmap?>(null, asset, targetWidth) {
        value = asset?.let { a ->
            withContext(Dispatchers.IO) { PhotoLoader.load(context, a, targetWidth)?.asImageBitmap() }
        }
    }
    val b = bitmap
    if (b != null) {
        Image(bitmap = b, contentDescription = "Photo of ${plane.name}", modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
