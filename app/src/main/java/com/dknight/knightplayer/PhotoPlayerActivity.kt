package com.dknight.knightplayer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_photo_player.galleryImage1
import kotlinx.android.synthetic.main.activity_photo_player.galleryImage2
import kotlinx.android.synthetic.main.activity_photo_player.loadingProgressBar
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 111

class PhotoPlayerActivity : AppCompatActivity() {

    private var loadGalleryImagesDisposable = Disposables.disposed()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_player)
    }

    override fun onStart() {
        super.onStart()
        checkForPermissions()
    }

    override fun onStop() {
        super.onStop()
        loadGalleryImagesDisposable.dispose()
    }

    private fun checkForPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            ) {
                Toast.makeText(
                    this,
                    "Need read external storage permission to show gallery images!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_READ_EXTERNAL_STORAGE
                )
            }
        } else {
            loadGalleryImages()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_READ_EXTERNAL_STORAGE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    loadGalleryImages()
                }
            }
        }
    }

    data class GalleryImage(val uri: String)

    private fun loadGalleryImages() {
        if (!loadGalleryImagesDisposable.isDisposed) {
            return
        }
        loadingProgressBar.visibility = View.VISIBLE

        loadGallerySwitcherImages().flatMapObservable { images ->
            Observable.interval(3, TimeUnit.SECONDS)
                .map { images }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = {
                    loadImage(uri = it.random().uri)
                },
                onError = {
                    Timber.e(it, "D7 Error loading gallery Images!")
                }
            ).also { loadGalleryImagesDisposable = it }
    }

    private fun loadGallerySwitcherImages(count: Int = 10): Single<List<GalleryImage>> {
        return Single.fromCallable {
            val screenshots = mutableListOf<GalleryImage>()
            val uri = MediaStore.Files.getContentUri("external")
            contentResolver.query(
                uri, arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA
                ), null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext() && screenshots.size < count) {
                    val path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                    val screenshotUri = Uri.parse(path).toString()
                    if (path.isImagePath) {
                        screenshots.add(GalleryImage(screenshotUri))
                    }
                }
            }
            screenshots.toList()
        }
    }

    private fun loadImage(uri: String) {
        val imageViewToLoad = if (galleryImage1.visibility == VISIBLE) {
            galleryImage1
        } else {
            galleryImage2
        }
        Glide.with(this)
            .load(uri)
            .addListener(object : RequestListener<Drawable> {

                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    if (galleryImage1.visibility == VISIBLE) {
                        galleryImage1.visibility = INVISIBLE
                        galleryImage2.visibility = VISIBLE
                    } else {
                        galleryImage2.visibility = INVISIBLE
                        galleryImage1.visibility = VISIBLE
                    }
                    loadingProgressBar.visibility = GONE
                    return false
                }
            })
            .into(imageViewToLoad)
    }

    private val String.isImagePath: Boolean
        get() {
            return endsWith(".jpg") || endsWith(".png")
        }
}