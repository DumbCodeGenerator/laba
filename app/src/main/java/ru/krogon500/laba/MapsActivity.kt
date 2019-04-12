package ru.krogon500.laba

import android.Manifest
import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.android.synthetic.main.images_bottom_sheet.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.LinkedHashMap


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private val REQUEST_TAKE_PHOTO = 1
    private val PERM_REQ_CODE = 322
    private var PREVIOUS_STATE = BottomSheetBehavior.STATE_COLLAPSED
    private lateinit var mMap: GoogleMap
    private lateinit var dbHelper: DbHelper
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<RecyclerView>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mCurrentPhotoPath: String? = null
    private var delIsShow = false
    private val markersMap: LinkedHashMap<LatLng, Marker> = LinkedHashMap()
    private var currentMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        fusedLocationClient = FusedLocationProviderClient(this)
        bottomSheetBehavior = BottomSheetBehavior.from(images_view)
        dbHelper = DbHelper(this, DbHelper.DATABASE_NAME)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermission(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION))
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
                PERM_REQ_CODE)

    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun hasPermission(vararg permissions: String): Boolean{
        permissions.forEach {
            if(checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED){
                return false
            }
        }
        return true
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            mCurrentPhotoPath = absolutePath
        }
    }

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        packageName,
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO)
                }
            }
        }
    }

    fun View.hideDelBtn(){
        val layoutParams = this.layoutParams as CoordinatorLayout.LayoutParams
        val del_leftmargin = layoutParams.leftMargin
        this.animate().translationX(-(this.width + del_leftmargin).toFloat()).setInterpolator(LinearInterpolator()).setDuration(150).start()
        delIsShow = false
    }

    fun View.showDelBtn(){
        this.animate().translationX(0f).setInterpolator(LinearInterpolator()).setDuration(150).start()
        delIsShow = true
    }

    private fun LatLng.string() : String{
        return "${this.latitude}/${this.longitude}"
    }

    private fun String.toLatLng(): LatLng{
        val location = this.split("/")
        val latitude = location[0].toDouble()
        val longitude = location[1].toDouble()
        return LatLng(latitude, longitude)
    }

    private fun getImages(markerPos: LatLng){
        val images = ArrayList<String>()
        val imagesCursor = dbHelper.selectImagesFromMarker(DbHelper.IMAGES_TABLE_NAME, markerPos.string())
        if(imagesCursor.moveToFirst()){
            while (!imagesCursor.isAfterLast){
                images.add(imagesCursor.getString(imagesCursor.getColumnIndex(DbHelper.IMAGE)))
                imagesCursor.moveToNext()
            }
        }
        imagesCursor.close()

        images_view.adapter = ImagesAdapter(this, images)
    }

    override fun onBackPressed() {
        if(bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED || delIsShow) {
            delButton.hideDelBtn()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else
            super.onBackPressed()
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TAKE_PHOTO && resultCode == RESULT_OK) {
            val adapter = images_view.adapter as? ImagesAdapter ?: return
            adapter.addImage(mCurrentPhotoPath!!)
            if(currentMarker != null) {
                val values = ContentValues()
                values.put(DbHelper.IMAGE, mCurrentPhotoPath)
                values.put(DbHelper.MARKER, currentMarker?.position?.string())
                try {
                    dbHelper.insertRow(DbHelper.IMAGES_TABLE_NAME, values)
                } catch (e: Exception) {
                    Log.e("lol", e.localizedMessage)
                }
            }
        }else if(requestCode == PERM_REQ_CODE && resultCode == RESULT_OK && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && !mMap.isMyLocationEnabled){
            mMap.isMyLocationEnabled = true
        }
    }

    private fun onMyLocationButtonClick(): Boolean{
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
            val lastLocation = fusedLocationClient.lastLocation
            lastLocation.addOnCompleteListener {
                val markersDist = mutableListOf<LocAndDistance>()
                markersMap.values.forEach { marker ->
                    val distance = it.result?.distanceTo(Location("marker").apply {
                        latitude = marker.position.latitude
                        longitude = marker.position.longitude
                    }) ?: return@addOnCompleteListener
                    markersDist.add(LocAndDistance(marker.position, distance))
                }
                val nearestLocation = markersDist.minBy { result -> result.distance }?.location ?: return@addOnCompleteListener
                zoomIn(nearestLocation)

                //Если надо будет затриггерить клик на маркер
                //onMarkerClick(nearestLocation)

                Toast.makeText(this, String.format("Ближайшая точка с координатами: %.0f/%.0f", nearestLocation.latitude, nearestLocation.longitude), Toast.LENGTH_SHORT).show()
            }
            return true
        }else{
            Toast.makeText(this, "Нет разрешения на определение местоположения", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun zoomIn(location: LatLng?) {
        location ?: return
        val cameraPosition = CameraPosition.Builder()
            .target(location)
            .zoom(6f).build()
        //Zoom in and animate the camera.
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null)
    }

    private fun onMarkerClick(location: LatLng): Boolean{
        currentMarker = markersMap[location]
        getImages(location)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        //fab.show()
        delButton.showDelBtn()
        return true
    }

    private class LocAndDistance(val location: LatLng, val distance: Float)

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.apply {
            isZoomControlsEnabled = true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            mMap.isMyLocationEnabled = true

        mMap.setOnMyLocationButtonClickListener {
            onMyLocationButtonClick()
        }
        images_view.layoutManager = GridLayoutManager(this, 3)

        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback(){
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if(newState == BottomSheetBehavior.STATE_COLLAPSED && PREVIOUS_STATE == BottomSheetBehavior.STATE_EXPANDED) {
                    delButton.showDelBtn()
                    fab.hide()
                    PREVIOUS_STATE = newState
                } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                    delButton.hideDelBtn()
                    fab.show()
                    PREVIOUS_STATE = newState
                }else if(newState == BottomSheetBehavior.STATE_HALF_EXPANDED)
                    PREVIOUS_STATE = newState
            }

        })

        val markers = dbHelper.selectMarkers(DbHelper.MARKERS_TABLE_NAME)
        if(markers.moveToFirst()){
            while (!markers.isAfterLast){
                val location = markers.getString(markers.getColumnIndex(DbHelper.MARKER))
                val marker = mMap.addMarker(MarkerOptions().position(location.toLatLng()))
                markersMap[marker.position] = marker
                markers.moveToNext()
            }
        }
        markers.close()
        currentMarker = markersMap.values.last()

        mMap.setOnMapClickListener {
            if(bottomSheetBehavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            if(delIsShow)
                delButton.hideDelBtn()
        }

        mMap.setOnMapLongClickListener {
            val values = ContentValues()
            values.put(DbHelper.MARKER, it.string())
            try {
                dbHelper.insertRow(DbHelper.MARKERS_TABLE_NAME, values)
            }catch (e: Exception){
                Log.e("laba", e.localizedMessage)
            }
            val marker = mMap.addMarker(MarkerOptions().position(it))
            markersMap[marker.position] = marker
        }

        mMap.setOnMarkerClickListener {
            onMarkerClick(it.position)
        }

        delButton.setOnClickListener {
            if(bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED || bottomSheetBehavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

            if(currentMarker != null){
                val imagesCursor = dbHelper.selectImagesFromMarker(DbHelper.IMAGES_TABLE_NAME, currentMarker?.position?.string() ?: return@setOnClickListener)
                if (imagesCursor.moveToFirst()){
                    while (!imagesCursor.isAfterLast) {
                        val success = File(imagesCursor.getString(imagesCursor.getColumnIndex(DbHelper.IMAGE))).delete()
                        Log.i("laba", "file deleted: $success")
                        imagesCursor.moveToNext()
                    }
                }
                imagesCursor.close()

                try {
                    dbHelper.deleteRows(DbHelper.MARKERS_TABLE_NAME, DbHelper.MARKER, currentMarker!!.position.string())
                    dbHelper.deleteRows(DbHelper.IMAGES_TABLE_NAME, DbHelper.MARKER, currentMarker!!.position.string())
                }catch (e: Exception){
                    Log.e("laba", e.localizedMessage)
                }

                currentMarker!!.remove()
                currentMarker = null
                it.hideDelBtn()
            }
        }

        fab.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasPermission(Manifest.permission.CAMERA) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                dispatchTakePictureIntent()
            else
                Toast.makeText(this, "Нет разрешения на использование камеры", Toast.LENGTH_LONG).show()
        }

        if(currentMarker != null)
            mMap.moveCamera(CameraUpdateFactory.newLatLng(currentMarker!!.position))
    }
}
