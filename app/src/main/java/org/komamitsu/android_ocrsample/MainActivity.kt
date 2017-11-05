package org.komamitsu.android_ocrsample

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat.startActivityForResult
import android.text.method.ScrollingMovementMethod
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.widget.TextView

import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextBlock
import com.google.android.gms.vision.text.TextRecognizer

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.regex.Pattern

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.Manifest.permission.CAMERA
import android.os.Environment
import android.os.PersistableBundle
import android.support.v4.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    companion object {
        private val REQUEST_GALLERY = 0
        private val REQUEST_CAMERA = 1
        private val PERMISSION_REQUEST_CODE = 200
        private val TAG = "MainActivity"
        private val REQUEST_PERMISSION = 1
    }

    private var imageUri: Uri? = null
    private var detectedTextView: TextView? = null

    private var mCurrentPhotoPath = ""

    fun createImageFile(): File{
        // Create an image file name
        val timeStamp = System.currentTimeMillis().toString()
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName,  /* prefix */
            ".jpg",         /* suffix */
            storageDir      /* directory */
        )
        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.absolutePath
        return image
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSION -> if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById(R.id.choose_from_gallery).setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(intent, REQUEST_GALLERY)
        }

        findViewById(R.id.take_a_photo).setOnClickListener {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION)
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION)
        }else{
            var photoFile = createImageFile()
            Log.d("hi", "there")
            if (photoFile != null) {
                imageUri = Uri.fromFile(photoFile);
//                val imageUri = FileProvider.getUriForFile(this,"org.komamitsu.android_ocrsample.fileprovider", photoFile)
                Log.d("uri: ", imageUri.toString())
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                Log.d("putting", "extras")
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
                Log.d("start", "activity4result")
                startActivityForResult(intent, REQUEST_CAMERA)
                Log.d("tag", "done")
            }
        }


        }

        detectedTextView = findViewById(R.id.detected_text) as TextView
        detectedTextView!!.movementMethod = ScrollingMovementMethod()

    }

    private fun inspectFromBitmap(bitmap: Bitmap) {
        val textRecognizer = TextRecognizer.Builder(this).build()
        try {
            if (!textRecognizer.isOperational) {
                AlertDialog.Builder(this).setMessage("Text recognizer could not be set up on your device").show()
                return
            }

            val frame = Frame.Builder().setBitmap(bitmap).build()
            val origTextBlocks = textRecognizer.detect(frame)
            val textBlocks = ArrayList<TextBlock>()
            for (i in 0..origTextBlocks.size() - 1) {
                val textBlock = origTextBlocks.valueAt(i)
                textBlocks.add(textBlock)
            }
            Collections.sort(textBlocks, Comparator<TextBlock> { o1, o2 ->
                val diffOfTops = o1.boundingBox.top - o2.boundingBox.top
                val diffOfLefts = o1.boundingBox.left - o2.boundingBox.left
                if (diffOfTops != 0) {
                    return@Comparator diffOfTops
                }
                diffOfLefts
            })

            val detectedText = StringBuilder()
            for (textBlock in textBlocks) {
                if (textBlock != null && textBlock.value != null) {
                    detectedText.append(textBlock.value)
                    detectedText.append("\n")
                }
            }
            val detectedTextString = detectedText.toString()
            val pattern = Pattern.compile("Password:(.+)")
            val matcher = pattern.matcher(detectedTextString)
            var passwordString = ""
            if (matcher.find()){
                passwordString = matcher.group(1)
            }
            detectedTextView!!.text = passwordString
//              detectedTextView!!.text = detectedTextString
            val networkSSID = "Academia-Guest"

            val networkPass = passwordString
            var conf = WifiConfiguration()
            conf.SSID = "\"" + networkSSID + "\""
            conf.preSharedKey = "\""+ networkPass +"\"";
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.addNetwork(conf)
            val list = wifiManager.getConfiguredNetworks()
            for (i in list) {
                if (i.SSID != null && i.SSID == "\"" + networkSSID + "\"") {
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(i.networkId, true)
                    wifiManager.reconnect()
                    break
                }
            }
        } finally {
            textRecognizer.release()
        }
    }

    private fun inspect(uri: Uri) {
        Log.d("URI:    ", uri.toString())
        var `is`: InputStream? = null
        var bitmap: Bitmap? = null
        try {
            `is` = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            options.inSampleSize = 2
            options.inScreenDensity = DisplayMetrics.DENSITY_LOW
            bitmap = BitmapFactory.decodeStream(`is`, null, options)
            inspectFromBitmap(bitmap)
        } catch (e: FileNotFoundException) {
            Log.w(TAG, "Failed to find the file: " + uri, e)
        } finally {
            if (bitmap != null) {
                bitmap.recycle()
            }
            if (`is` != null) {
                try {
                    `is`.close()
                } catch (e: IOException) {
                    Log.w(TAG, "Failed to close InputStream", e)
                }

            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        val imageUrl = imageUri.toString()!!
        outState!!.putString("uri", imageUrl)
        super.onSaveInstanceState(outState, outPersistentState)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d("at: ", "onActivityResult")
        when (requestCode) {
        REQUEST_GALLERY ->  inspect(data!!.data)
        REQUEST_CAMERA -> {
            if (resultCode == RESULT_OK) {
                inspect(imageUri!!)
            }
        }
        else -> {return}


        }

    }


}
