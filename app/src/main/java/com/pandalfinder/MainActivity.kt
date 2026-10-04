package com.pandalfinder

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.pandalfinder.data.*
import com.pandalfinder.ui.HoppingAdapter
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MainActivity : AppCompatActivity() {

    // ── Map Views ──
    private lateinit var mapContainer: View
    private lateinit var mapView: MapView
    private lateinit var searchInput: TextInputEditText
    private lateinit var results: RecyclerView
    private lateinit var searchEmptyView: TextView
    private lateinit var statusCard: View
    private lateinit var dismissStatusCard: View
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var allowButton: MaterialButton
    private lateinit var pandalsFilterButton: MaterialButton
    private lateinit var metroFilterButton: MaterialButton
    private lateinit var toiletsFilterButton: MaterialButton
    private lateinit var zonesFilterButton: MaterialButton
    private lateinit var btnNearbyFloating: View
    private lateinit var crowdLegendPill: View
    private lateinit var crowdLegendText: TextView
    private lateinit var offlineBanner: View
    private lateinit var festivalAlertCard: View
    private lateinit var festivalAlertText: TextView
    private lateinit var dismissFestivalAlert: ImageButton

    // ── Compact Floating Metro Card Views ──
    private lateinit var metroCard: MaterialCardView
    private lateinit var metroCardIconContainer: FrameLayout
    private lateinit var metroCardIcon: ImageView
    private lateinit var metroCardName: TextView
    private lateinit var metroCardDistance: TextView
    private lateinit var dismissMetroCard: ImageButton
    private lateinit var metroAddToHoppingCard: MaterialCardView
    private lateinit var metroHoppingActionIcon: ImageView
    private lateinit var metroNavigateButton: MaterialButton

    // ── Compact Floating Toilet Card Views ──
    private lateinit var toiletCard: MaterialCardView
    private lateinit var toiletCardIconContainer: FrameLayout
    private lateinit var toiletCardIcon: ImageView
    private lateinit var toiletCardName: TextView
    private lateinit var toiletCardDistance: TextView
    private lateinit var dismissToiletCard: ImageButton
    private lateinit var toiletAddToHoppingCard: MaterialCardView
    private lateinit var toiletHoppingActionIcon: ImageView
    private lateinit var toiletNavigateButton: MaterialButton

    // ── Hopping Views ──
    private lateinit var hoppingContainer: View
    private lateinit var hoppingEmptyView: View
    private lateinit var hoppingRecyclerView: RecyclerView
    private lateinit var hoppingSummaryCard: View
    private lateinit var hoppingTotalDistance: TextView
    private lateinit var hoppingEstimatedTime: TextView
    private lateinit var hoppingStopsCount: TextView
    private lateinit var hoppingContextSummaryText: TextView
    private lateinit var btnOptimizeRoute: MaterialButton
    private lateinit var btnTonightsPlan: MaterialButton
    private lateinit var emptyStateTonightsPlanButton: MaterialButton
    private lateinit var startHoppingButton: MaterialButton
    private lateinit var hoppingAdapter: HoppingAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // ── Floating Navigation Pill Views (Map, Hopping, My Pandal) ──
    private lateinit var navMapPill: LinearLayout
    private lateinit var navMapIcon: ImageView
    private lateinit var navMapText: TextView
    private lateinit var navHoppingPill: LinearLayout
    private lateinit var navHoppingIcon: ImageView
    private lateinit var navHoppingText: TextView
    private lateinit var navHoppingBadge: TextView
    private lateinit var navMyPandalPill: LinearLayout
    private lateinit var navMyPandalIcon: ImageView
    private lateinit var navMyPandalText: TextView

    // ── Repositories ──
    private lateinit var pandals: PandalRepository
    private lateinit var weather: WeatherRepository
    private lateinit var weatherReports: WeatherReportRepository
    private lateinit var crowd: CrowdRepository
    private lateinit var metro: MetroRepository
    private lateinit var places: GooglePlacesRepository
    private lateinit var routes: GoogleRoutesRepository
    private lateinit var hopping: HoppingRepository
    private lateinit var eligibility: ContributionEligibility
    private lateinit var favorites: FavoritesRepository
    private lateinit var passport: PassportRepository
    private lateinit var zones: FestivalZoneRepository
    private lateinit var alerts: FestivalAlertRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var photos: PhotoRepository
    private lateinit var authRepo: AuthRepository
    private lateinit var contributions: ContributionsRepository
    private lateinit var adminRepo: AdminRepository
    private lateinit var ratingRepo: RatingRepository
    private lateinit var rainReportRepo: RainReportRepository
    private var isResolvingLocationSettings = false
    private var lastLocationPromptTime = 0L

    private val locationPrefs by lazy {
        getSharedPreferences("pandalquest_location_prefs", Context.MODE_PRIVATE)
    }

    private var hasRequestedLocationPermission: Boolean
        get() = locationPrefs.getBoolean(KEY_HAS_REQUESTED_LOCATION_PERMISSION, false)
        set(value) = locationPrefs.edit().putBoolean(KEY_HAS_REQUESTED_LOCATION_PERMISSION, value).apply()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
        val anyGranted = fineGranted || coarseGranted

        Log.d(LOCATION_TAG, "fine=$fineGranted")
        Log.d(LOCATION_TAG, "coarse=$coarseGranted")
        Log.d(LOCATION_TAG, "permission result: fine=$fineGranted, coarse=$coarseGranted, anyGranted=$anyGranted")

        if (anyGranted) {
            Log.d(LOCATION_TAG, "permission granted")
            Log.d(LOCATION_TAG, "checking device location services")
            dismissLocationOffBanner()
            checkLocationSettingsAndStart(explicitUserAction = true)
        } else {
            Log.d(LOCATION_TAG, "permission denied")
            handleLocationPermissionDenied()
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingUploadPandalId: String? = null
    private var pendingUploadPandal: Pandal? = null
    private var pendingReplacePhoto: com.pandalfinder.data.PandalPhoto? = null
    private var onPhotoReplacedCallback: (() -> Unit)? = null
    private var tempCameraUri: Uri? = null

    // Multi-photo upload dialog & adapter references
    private var activeMultiPhotoDialog: androidx.appcompat.app.AlertDialog? = null
    private var activeSelectedPhotosAdapter: com.pandalfinder.ui.SelectedPhotosAdapter? = null
    private val selectedPhotoItems = mutableListOf<com.pandalfinder.ui.SelectedPhotoItem>()

    private val singleGalleryPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val replacePhoto = pendingReplacePhoto
            if (replacePhoto != null) {
                val cb = onPhotoReplacedCallback
                pendingReplacePhoto = null
                onPhotoReplacedCallback = null
                showPhotoReplacePreview(replacePhoto, uri, cb)
            }
        } else {
            pendingReplacePhoto = null
            onPhotoReplacedCallback = null
        }
    }

    private val multiGalleryPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            val replacePhoto = pendingReplacePhoto
            if (replacePhoto != null) {
                val cb = onPhotoReplacedCallback
                pendingReplacePhoto = null
                onPhotoReplacedCallback = null
                val firstUri = uris.firstOrNull()
                if (firstUri != null) {
                    showPhotoReplacePreview(replacePhoto, firstUri, cb)
                }
            } else {
                val p = pendingUploadPandal ?: currentDetailPandal
                if (p != null) {
                    if (activeMultiPhotoDialog?.isShowing == true) {
                        addUrisToMultiPreview(uris)
                    } else {
                        showMultiPhotoUploadPreview(p, uris)
                    }
                }
            }
        } else {
            pendingReplacePhoto = null
            onPhotoReplacedCallback = null
        }
    }

    private val cameraLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success && tempCameraUri != null) {
            val uri = tempCameraUri!!
            val replacePhoto = pendingReplacePhoto
            if (replacePhoto != null) {
                val cb = onPhotoReplacedCallback
                pendingReplacePhoto = null
                onPhotoReplacedCallback = null
                showPhotoReplacePreview(replacePhoto, uri, cb)
            } else {
                val p = pendingUploadPandal ?: currentDetailPandal
                if (p != null) {
                    if (activeMultiPhotoDialog?.isShowing == true) {
                        addUrisToMultiPreview(listOf(uri))
                    } else {
                        showMultiPhotoUploadPreview(p, listOf(uri))
                    }
                }
            }
        } else {
            pendingReplacePhoto = null
            onPhotoReplacedCallback = null
        }
    }

    private fun addUrisToMultiPreview(newUris: List<Uri>) {
        val existingUris = selectedPhotoItems.map { it.uri }.toSet()
        val uniqueNew = newUris.filterNot { existingUris.contains(it) }

        val availableSlots = 10 - selectedPhotoItems.size
        if (uniqueNew.size > availableSlots) {
            Toast.makeText(this, "Maximum 10 photos per upload.", Toast.LENGTH_SHORT).show()
        }
        val toAdd = uniqueNew.take(availableSlots)
        if (toAdd.isNotEmpty()) {
            val startPos = selectedPhotoItems.size
            toAdd.forEach { selectedPhotoItems.add(com.pandalfinder.ui.SelectedPhotoItem(it)) }
            activeSelectedPhotosAdapter?.notifyItemRangeInserted(startPos, toAdd.size)
            activeSelectedPhotosAdapter?.notifyItemChanged(selectedPhotoItems.size)
        }
    }

    private fun showChoosePhotoSourceDialog(pandal: Pandal) {
        pendingUploadPandal = pandal
        pendingReplacePhoto = null
        onPhotoReplacedCallback = null
        val view = layoutInflater.inflate(R.layout.dialog_choose_photo_source, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.chooseSourcePandalName).text = pandal.name

        view.findViewById<View>(R.id.sourceCameraCard).setOnClickListener {
            sheet.dismiss()
            val tempFile = java.io.File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }

        view.findViewById<View>(R.id.sourceGalleryCard).setOnClickListener {
            sheet.dismiss()
            multiGalleryPicker.launch("image/*")
        }

        sheet.show()
    }

    private fun showReplacePhotoSourceDialog(photo: com.pandalfinder.data.PandalPhoto, pandalName: String, onReplaced: () -> Unit) {
        pendingReplacePhoto = photo
        onPhotoReplacedCallback = onReplaced
        pendingUploadPandal = null
        val view = layoutInflater.inflate(R.layout.dialog_choose_photo_source, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.chooseSourcePandalName).text = "Replace photo: $pandalName"

        view.findViewById<View>(R.id.sourceCameraCard).setOnClickListener {
            sheet.dismiss()
            val tempFile = java.io.File(cacheDir, "camera_replace_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        }

        view.findViewById<View>(R.id.sourceGalleryCard).setOnClickListener {
            sheet.dismiss()
            singleGalleryPicker.launch("image/*")
        }

        sheet.show()
    }

    private fun showPhotoReplacePreview(
        photo: com.pandalfinder.data.PandalPhoto,
        uri: Uri,
        onReplaced: (() -> Unit)?
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_photo_preview, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val pandal = pandals.all().find { it.id == photo.pandalId || "pandal:${it.id}" == photo.pandalId }
        view.findViewById<TextView>(R.id.photoPreviewSubtitle).text = "Replace photo for ${pandal?.name ?: "Pandal"}"
        val previewImg = view.findViewById<ImageView>(R.id.photoPreviewImage)
        previewImg.setImageURI(uri)

        view.findViewById<View>(R.id.photoPreviewCancelBtn).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.photoPreviewUploadBtn).setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Uploading replacement photo...", Toast.LENGTH_SHORT).show()
            photos.replacePhoto(
                context = this,
                oldPhoto = photo,
                newImageUri = uri,
                onProgress = { /* progress */ },
                onComplete = { result ->
                    mainHandler.post {
                        result.onSuccess {
                            Toast.makeText(this, "Photo updated successfully!", Toast.LENGTH_SHORT).show()
                            onReplaced?.invoke()
                        }.onFailure { err ->
                            val errMsg = err.localizedMessage ?: "Failed to replace photo."
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Update Failed")
                                .setMessage(errMsg)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            )
        }

        dialog.show()
    }

    private fun showMultiPhotoUploadPreview(pandal: Pandal, initialUris: List<Uri>) {
        val view = layoutInflater.inflate(R.layout.dialog_multi_photo_preview, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        activeMultiPhotoDialog = dialog
        selectedPhotoItems.clear()

        val uniqueUris = initialUris.distinct()
        if (uniqueUris.size > 10) {
            Toast.makeText(this, "Maximum 10 photos per upload. Selected first 10.", Toast.LENGTH_SHORT).show()
        }
        uniqueUris.take(10).forEach { uri ->
            selectedPhotoItems.add(com.pandalfinder.ui.SelectedPhotoItem(uri))
        }

        val subtitleView = view.findViewById<TextView>(R.id.multiPhotoSubtitle)
        val countTextView = view.findViewById<TextView>(R.id.multiPhotoCountText)
        val recyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.selectedPhotosRecyclerView)
        val progressLayout = view.findViewById<View>(R.id.batchProgressLayout)
        val progressLabel = view.findViewById<TextView>(R.id.batchProgressLabel)
        val progressPercent = view.findViewById<TextView>(R.id.batchProgressPercent)
        val progressBar = view.findViewById<ProgressBar>(R.id.batchProgressBar)
        val summaryText = view.findViewById<TextView>(R.id.batchSummaryText)
        val cancelBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.multiPhotoCancelBtn)
        val uploadBtn = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.multiPhotoUploadBtn)

        subtitleView.text = pandal.name

        fun updateUIState() {
            val count = selectedPhotoItems.size
            countTextView.text = "$count photo${if (count != 1) "s" else ""} selected (Max 10)"
            uploadBtn.text = "Upload $count Photo${if (count != 1) "s" else ""}"
            uploadBtn.isEnabled = count > 0
        }

        lateinit var adapter: com.pandalfinder.ui.SelectedPhotosAdapter
        adapter = com.pandalfinder.ui.SelectedPhotosAdapter(
            items = selectedPhotoItems,
            maxLimit = 10,
            onRemove = { index ->
                if (index in selectedPhotoItems.indices) {
                    selectedPhotoItems.removeAt(index)
                    adapter.notifyItemRemoved(index)
                    adapter.notifyItemRangeChanged(index, selectedPhotoItems.size - index + 1)
                    updateUIState()
                    if (selectedPhotoItems.isEmpty()) {
                        dialog.dismiss()
                    }
                }
            },
            onAddMore = {
                showChoosePhotoSourceDialog(pandal)
            }
        )
        activeSelectedPhotosAdapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = adapter

        updateUIState()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        fun startBatchUpload(itemsToUpload: List<com.pandalfinder.ui.SelectedPhotoItem>) {
            if (itemsToUpload.isEmpty()) return

            adapter.setUploading(true)
            cancelBtn.isEnabled = false
            uploadBtn.isEnabled = false
            summaryText.visibility = View.GONE
            progressLayout.visibility = View.VISIBLE

            val totalCount = itemsToUpload.size
            progressBar.max = totalCount * 100
            progressBar.progress = 0
            progressLabel.text = "Uploading photos (1 of $totalCount)..."
            progressPercent.text = "0%"

            val urisToUpload = itemsToUpload.map { it.uri }

            photos.uploadPhotosBatch(
                context = this,
                pandalId = pandal.id,
                imageUris = urisToUpload,
                onItemStart = { idx, _ ->
                    mainHandler.post {
                        val itemIndex = selectedPhotoItems.indexOfFirst { it.uri == urisToUpload[idx] }
                        if (itemIndex != -1) {
                            adapter.updateItemStatus(itemIndex, com.pandalfinder.ui.UploadStatus.UPLOADING)
                        }
                        progressLabel.text = "Uploading photo (${idx + 1} of $totalCount)..."
                    }
                },
                onItemProgress = { idx, itemProgress ->
                    mainHandler.post {
                        val currentOverall = idx * 100 + itemProgress
                        progressBar.progress = currentOverall
                        val pct = ((currentOverall.toFloat() / (totalCount * 100)) * 100).toInt().coerceIn(0, 100)
                        progressPercent.text = "$pct%"
                    }
                },
                onItemComplete = { idx, uri, result ->
                    mainHandler.post {
                        val itemIndex = selectedPhotoItems.indexOfFirst { it.uri == uri }
                        if (itemIndex != -1) {
                            val status = if (result.isSuccess) com.pandalfinder.ui.UploadStatus.SUCCESS else com.pandalfinder.ui.UploadStatus.FAILED
                            val errMsg = result.exceptionOrNull()?.localizedMessage
                            adapter.updateItemStatus(itemIndex, status, errMsg)
                        }
                    }
                },
                onBatchComplete = { successCount, failedCount, results ->
                    mainHandler.post {
                        adapter.setUploading(false)
                        cancelBtn.isEnabled = true
                        cancelBtn.text = "Close"
                        progressLayout.visibility = View.GONE

                        if (failedCount == 0) {
                            Toast.makeText(this, "$successCount photo${if (successCount != 1) "s" else ""} uploaded successfully!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        } else if (successCount > 0) {
                            summaryText.visibility = View.VISIBLE
                            summaryText.text = "$successCount of $totalCount photos uploaded. $failedCount photo(s) failed."
                            uploadBtn.text = "Retry $failedCount Failed Photo${if (failedCount != 1) "s" else ""}"
                            uploadBtn.isEnabled = true
                            uploadBtn.setOnClickListener {
                                val failedItems = selectedPhotoItems.filter { it.status == com.pandalfinder.ui.UploadStatus.FAILED }
                                startBatchUpload(failedItems)
                            }
                        } else {
                            summaryText.visibility = View.VISIBLE
                            summaryText.text = "All $failedCount photo(s) failed to upload. Check connection and retry."
                            uploadBtn.text = "Retry Upload"
                            uploadBtn.isEnabled = true
                            uploadBtn.setOnClickListener {
                                startBatchUpload(selectedPhotoItems)
                            }
                        }
                    }
                }
            )
        }

        uploadBtn.setOnClickListener {
            startBatchUpload(selectedPhotoItems)
        }

        dialog.setOnDismissListener {
            activeMultiPhotoDialog = null
            activeSelectedPhotosAdapter = null
            selectedPhotoItems.clear()
        }

        dialog.show()
    }

    private fun animateButtonPress(view: View, onEnd: (() -> Unit)? = null) {
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(90)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(140)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2.5f))
                    .withEndAction {
                        onEnd?.invoke()
                    }
                    .start()
            }
            .start()
    }

    private fun animateIconSpin(icon: View) {
        icon.animate()
            .rotationBy(360f)
            .setDuration(550)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    // ── State ──
    private var map: MapLibreMap? = null
    private var location: Location? = null
    private var shownPandals = emptyList<Pandal>()
    private var shownStations = emptyList<MetroStation>()
    private var shownToilets = emptyList<PublicToilet>()
    private var liveCrowdMap = mapOf<String, Int>()
    private var lastCrowdLoadedDay: String = ""
    private var refreshCurrentDetailCrowd: (() -> Unit)? = null
    private var filterShowPandals = true
    private var filterShowMetro = false
    private var filterShowToilets = false
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var currentDetailPandal: Pandal? = null
    private var currentDetailSheetView: View? = null
    private var currentSelectedMetro: MetroStation? = null
    private var currentSelectedToilet: PublicToilet? = null
    private var selectedMarkerPosition: LatLng? = null
    private val markerBitmapCache = mutableMapOf<String, Bitmap>()

    private val dateChangeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            val currentDay = getTodayDateKey()
            if (currentDay != lastCrowdLoadedDay) {
                lastCrowdLoadedDay = currentDay
                loadLiveCrowdHeatmap()
                refreshCurrentDetailCrowd?.invoke()
            }
        }
    }

    private fun getTodayDateKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}-${cal.get(java.util.Calendar.DAY_OF_MONTH)}"
    }

    // ────────────────────────────────────────────────────────
    //  Lifecycle
    // ────────────────────────────────────────────────────────

    override fun onCreate(state: Bundle?) {
        // Enforce warm Durga Puja festival light mode globally (eliminates system dark mode black screen flash)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(state)

        // Initialize Firebase
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            try {
                com.google.firebase.FirebaseApp.initializeApp(this)
                Log.d(TAG, "Firebase initialized successfully from google-services")
            } catch (e: Exception) {
                try {
                    com.google.firebase.FirebaseApp.initializeApp(
                        this,
                        com.google.firebase.FirebaseOptions.Builder()
                            .setApiKey("AIzaSyAB6FzuFNUd9OCd0nciRTKr8crKfSrJdAc")
                            .setApplicationId("1:333025667530:android:d653e917a23e08c1694733")
                            .setDatabaseUrl("https://pandalquest-default-rtdb.firebaseio.com")
                            .setProjectId("pandalquest")
                            .setStorageBucket("pandalquest.firebasestorage.app")
                            .build()
                    )
                    Log.d(TAG, "Firebase initialized successfully via fallback options")
                } catch (e2: Exception) {
                    Log.e(TAG, "Firebase initialization failed", e2)
                }
            }
        }

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // Initialize Data Repositories
        pandals = PandalRepository(this)
        weather = WeatherRepository()
        weatherReports = WeatherReportRepository()
        crowd = CrowdRepository()
        metro = MetroRepository()
        places = GooglePlacesRepository()
        routes = GoogleRoutesRepository()
        hopping = HoppingRepository(this, pandals)
        eligibility = ContributionEligibility(this)
        favorites = FavoritesRepository(this)
        passport = PassportRepository(this)
        zones = FestivalZoneRepository(pandals)
        alerts = FestivalAlertRepository()
        networkMonitor = NetworkMonitor(this)
        photos = PhotoRepository()
        authRepo = AuthRepository()
        authRepo.initAndSignInAnonymously()
        contributions = ContributionsRepository()
        adminRepo = AdminRepository()
        ratingRepo = RatingRepository()
        rainReportRepo = RainReportRepository()

        initViews(state)
        startLaunchAnimation()
        setupHoppingTab()
        setupFloatingNav()
        setupNetworkAndAlerts()
        checkLocationSettingsAndStart(explicitUserAction = false)
        loadLiveCrowdHeatmap()
    }

    private fun startLaunchAnimation() {
        val launchOverlay = findViewById<View>(R.id.launchOverlay) ?: return
        val logoLens = findViewById<View>(R.id.launchLogoLens)
        val specularSweep = findViewById<View>(R.id.launchSpecularSweep)
        val appTitle = findViewById<View>(R.id.launchAppTitle)
        val appSubtitle = findViewById<View>(R.id.launchAppSubtitle)

        // Warm background is immediately visible from frame 1
        // Set initial state for animated elements
        logoLens?.alpha = 0f
        logoLens?.scaleX = 0.90f
        logoLens?.scaleY = 0.90f
        appTitle?.alpha = 0f
        appTitle?.translationY = dp(12).toFloat()
        appSubtitle?.alpha = 0f
        appSubtitle?.translationY = dp(10).toFloat()

        // 0–120ms: Logo alpha 0 -> 1, scale 0.90 -> 1.0
        logoLens?.animate()
            ?.alpha(1f)
            ?.scaleX(1.0f)
            ?.scaleY(1.0f)
            ?.setDuration(120)
            ?.setInterpolator(android.view.animation.DecelerateInterpolator())
            ?.withEndAction {
                // 120–350ms: Subtle glow moves across/behind logo
                specularSweep?.visibility = View.VISIBLE
                specularSweep?.translationX = -dp(60).toFloat()
                specularSweep?.animate()
                    ?.translationX(dp(60).toFloat())
                    ?.setDuration(230)
                    ?.setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    ?.withEndAction {
                        specularSweep.visibility = View.INVISIBLE
                    }
                    ?.start()
            }
            ?.start()

        // 350–500ms: PandalFinder text fades in with slight upward movement
        appTitle?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(350)
            ?.setDuration(150)
            ?.setInterpolator(android.view.animation.DecelerateInterpolator())
            ?.start()

        // 500–650ms: Subtitle fades in
        appSubtitle?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(500)
            ?.setDuration(150)
            ?.setInterpolator(android.view.animation.DecelerateInterpolator())
            ?.start()

        // 650–800ms: Seamless crossfade directly into map
        mainHandler.postDelayed({
            launchOverlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    launchOverlay.visibility = View.GONE
                }
                .start()
        }, 650)
    }

    private fun initViews(state: Bundle?) {
        mapContainer = findViewById(R.id.mapContainer)
        mapView = findViewById(R.id.mapView)
        searchInput = findViewById(R.id.searchInput)
        results = findViewById(R.id.searchResults)
        searchEmptyView = findViewById(R.id.searchEmptyView)
        statusCard = findViewById(R.id.statusCard)
        dismissStatusCard = findViewById(R.id.dismissStatusCard)
        statusTitle = findViewById(R.id.statusTitle)
        statusMessage = findViewById(R.id.statusMessage)
        allowButton = findViewById(R.id.allowLocationButton)
        offlineBanner = findViewById(R.id.offlineBanner)
        festivalAlertCard = findViewById(R.id.festivalAlertCard)
        festivalAlertText = findViewById(R.id.festivalAlertText)
        dismissFestivalAlert = findViewById(R.id.dismissFestivalAlert)

        dismissStatusCard.setOnClickListener {
            statusCard.visibility = View.GONE
        }
        dismissFestivalAlert.setOnClickListener {
            festivalAlertCard.visibility = View.GONE
        }

        pandalsFilterButton = findViewById(R.id.pandalsFilter)
        metroFilterButton = findViewById(R.id.nearbyFilter)
        toiletsFilterButton = findViewById(R.id.toiletsFilter)
        zonesFilterButton = findViewById(R.id.zonesFilter)
        btnNearbyFloating = findViewById(R.id.btnNearbyFloating)
        crowdLegendPill = findViewById(R.id.crowdLegendPill)
        crowdLegendText = findViewById(R.id.crowdLegendText)

        // Metro Card Views
        metroCard = findViewById(R.id.metroCard)
        metroCardIconContainer = findViewById(R.id.metroCardIconContainer)
        metroCardIcon = findViewById(R.id.metroCardIcon)
        metroCardName = findViewById(R.id.metroCardName)
        metroCardDistance = findViewById(R.id.metroCardDistance)
        dismissMetroCard = findViewById(R.id.dismissMetroCard)
        metroAddToHoppingCard = findViewById(R.id.metroAddToHoppingCard)
        metroHoppingActionIcon = findViewById(R.id.metroHoppingActionIcon)
        metroNavigateButton = findViewById(R.id.metroNavigateButton)

        dismissMetroCard.setOnClickListener {
            metroCard.visibility = View.GONE
            currentSelectedMetro = null
        }

        // Toilet Card Views
        toiletCard = findViewById(R.id.toiletCard)
        toiletCardIconContainer = findViewById(R.id.toiletCardIconContainer)
        toiletCardIcon = findViewById(R.id.toiletCardIcon)
        toiletCardName = findViewById(R.id.toiletCardName)
        toiletCardDistance = findViewById(R.id.toiletCardDistance)
        dismissToiletCard = findViewById(R.id.dismissToiletCard)
        toiletAddToHoppingCard = findViewById(R.id.toiletAddToHoppingCard)
        toiletHoppingActionIcon = findViewById(R.id.toiletHoppingActionIcon)
        toiletNavigateButton = findViewById(R.id.toiletNavigateButton)

        dismissToiletCard.setOnClickListener {
            toiletCard.visibility = View.GONE
            currentSelectedToilet = null
        }

        // Hopping Views
        hoppingContainer = findViewById(R.id.hoppingContainer)
        hoppingEmptyView = findViewById(R.id.hoppingEmptyView)
        hoppingRecyclerView = findViewById(R.id.hoppingRecyclerView)
        hoppingSummaryCard = findViewById(R.id.hoppingSummaryCard)
        hoppingTotalDistance = findViewById(R.id.hoppingTotalDistance)
        hoppingEstimatedTime = findViewById(R.id.hoppingEstimatedTime)
        hoppingStopsCount = findViewById(R.id.hoppingStopsCount)
        hoppingContextSummaryText = findViewById(R.id.hoppingContextSummaryText)
        btnOptimizeRoute = findViewById(R.id.btnOptimizeRoute)
        btnTonightsPlan = findViewById(R.id.btnTonightsPlan)
        emptyStateTonightsPlanButton = findViewById(R.id.emptyStateTonightsPlanButton)
        startHoppingButton = findViewById(R.id.startHoppingButton)

        // Floating Bottom Navigation Pills (Map, Hopping, My Pandal)
        navMapPill = findViewById(R.id.navMapPill)
        navMapIcon = findViewById(R.id.navMapIcon)
        navMapText = findViewById(R.id.navMapText)
        navHoppingPill = findViewById(R.id.navHoppingPill)
        navHoppingIcon = findViewById(R.id.navHoppingIcon)
        navHoppingText = findViewById(R.id.navHoppingText)
        navHoppingBadge = findViewById(R.id.navHoppingBadge)
        navMyPandalPill = findViewById(R.id.navMyPandalPill)
        navMyPandalIcon = findViewById(R.id.navMyPandalIcon)
        navMyPandalText = findViewById(R.id.navMyPandalText)

        results.layoutManager = LinearLayoutManager(this)
        allowButton.setOnClickListener { requestLocationPermission(isExplicitUserAction = true) }

        setupFilterPills()
        setupActionButtons()

        mapView.onCreate(state)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setOnMarkerClickListener { marker ->
                val pos = marker.position
                selectedMarkerPosition = pos

                val clickedPandal = shownPandals.firstOrNull {
                    it.latitude == pos.latitude && it.longitude == pos.longitude
                }
                if (clickedPandal != null) {
                    metroCard.visibility = View.GONE
                    toiletCard.visibility = View.GONE
                    currentSelectedMetro = null
                    currentSelectedToilet = null
                    renderMarkers(shownPandals, shownStations, shownToilets)
                    showDetail(clickedPandal)
                    return@setOnMarkerClickListener true
                }

                val clickedStation = shownStations.firstOrNull {
                    it.latitude == pos.latitude && it.longitude == pos.longitude
                }
                if (clickedStation != null) {
                    toiletCard.visibility = View.GONE
                    currentSelectedToilet = null
                    renderMarkers(shownPandals, shownStations, shownToilets)
                    showMetroCard(clickedStation)
                    return@setOnMarkerClickListener true
                }

                val clickedToilet = shownToilets.firstOrNull {
                    it.latitude == pos.latitude && it.longitude == pos.longitude
                }
                if (clickedToilet != null) {
                    metroCard.visibility = View.GONE
                    currentSelectedMetro = null
                    renderMarkers(shownPandals, shownStations, shownToilets)
                    showToiletCard(clickedToilet)
                    return@setOnMarkerClickListener true
                }
                false
            }
            readyMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                applyCurrentFilters()
            }
        }

        searchInput.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            if (query.isBlank()) {
                results.visibility = View.GONE
                searchEmptyView.visibility = View.GONE
            } else {
                val matching = pandals.searchAll(query, location, places.cachedToilets).take(10)
                if (matching.isEmpty()) {
                    results.visibility = View.GONE
                    searchEmptyView.visibility = View.VISIBLE
                } else {
                    searchEmptyView.visibility = View.GONE
                    results.visibility = View.VISIBLE
                    results.adapter = SearchResultAdapter(matching) { result ->
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)

                        searchInput.setText("")
                        searchInput.clearFocus()
                        results.visibility = View.GONE
                        searchEmptyView.visibility = View.GONE

                        if (!isValidCoordinate(result.latitude, result.longitude)) {
                            Log.w(TAG, "Search result has invalid coordinates: ${result.name}")
                            Toast.makeText(this, "Coordinates unavailable for ${result.name}", Toast.LENGTH_SHORT).show()
                            return@SearchResultAdapter
                        }

                        when (result.type) {
                            PlaceType.PANDAL -> {
                                val p = result.pandal ?: pandals.all().firstOrNull { it.id == result.id.removePrefix("pandal:") }
                                if (p != null) {
                                    focusPandal(p)
                                    showDetail(p)
                                }
                            }
                            PlaceType.METRO -> {
                                val s = result.metroStation ?: MetroStation.allStations().firstOrNull {
                                    it.name.equals(result.name, ignoreCase = true)
                                }
                                if (s != null) {
                                    focusMetro(s)
                                    showMetroCard(s)
                                }
                            }
                            PlaceType.TOILET -> {
                                val t = result.toilet ?: shownToilets.firstOrNull { it.id == result.id.removePrefix("toilet:") }
                                if (t != null) {
                                    focusToilet(t)
                                    showToiletCard(t)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupFilterPills() {
        pandalsFilterButton.setOnClickListener {
            filterShowPandals = !filterShowPandals
            updateFilterPillStyles()
            applyCurrentFilters()
        }
        metroFilterButton.setOnClickListener {
            filterShowMetro = !filterShowMetro
            updateFilterPillStyles()
            applyCurrentFilters()
        }
        toiletsFilterButton.setOnClickListener {
            filterShowToilets = !filterShowToilets
            updateFilterPillStyles()
            Log.d(GooglePlacesRepository.DIAGNOSTIC_TAG, "1. TOILETS toggle ${if (filterShowToilets) "enabled" else "disabled"}")
            if (filterShowToilets) {
                checkLocationSettingsAndStart(explicitUserAction = true) {
                    fetchAndDisplayToilets()
                }
                fetchAndDisplayToilets()
            } else {
                shownToilets = emptyList()
                applyCurrentFilters()
            }
        }

        zonesFilterButton.setOnClickListener {
            showExploreZonesSheet()
        }

        updateFilterPillStyles()
    }

    private fun setupActionButtons() {
        btnNearbyFloating.setOnClickListener {
            showNearbySheet()
        }

        crowdLegendPill.setOnClickListener {
            loadLiveCrowdHeatmap()
            Toast.makeText(this, "Live crowd updated from recent community reports", Toast.LENGTH_SHORT).show()
        }

        btnOptimizeRoute.setOnClickListener {
            showOptimizeRouteDialog()
        }

        btnTonightsPlan.setOnClickListener {
            showTonightsPlanSheet()
        }

        emptyStateTonightsPlanButton.setOnClickListener {
            showTonightsPlanSheet()
        }

        findViewById<View?>(R.id.btnMyLocation)?.setOnClickListener {
            handleMyLocationClick()
        }
    }

    private fun handleMyLocationClick() {
        if (!hasLocation()) {
            requestLocationPermission(isExplicitUserAction = true)
            return
        }

        val fusedClient = LocationServices.getFusedLocationProviderClient(this)
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        this.location = loc
                        val latLng = LatLng(loc.latitude, loc.longitude)
                        val cameraPosition = CameraPosition.Builder()
                            .target(latLng)
                            .zoom(15.0)
                            .build()
                        map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cameraPosition), 800)
                        Toast.makeText(this, "Centered on your location", Toast.LENGTH_SHORT).show()
                    } else {
                        fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                this.location = lastLoc
                                val latLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                val cameraPosition = CameraPosition.Builder()
                                    .target(latLng)
                                    .zoom(15.0)
                                    .build()
                                map?.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newCameraPosition(cameraPosition), 800)
                                Toast.makeText(this, "Centered on your location", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Acquiring GPS location… Please ensure GPS is enabled.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to get current location: ${e.message}", e)
                    Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission missing: ${e.message}", e)
            requestLocationPermission(isExplicitUserAction = true)
        }
    }

    private fun setupNetworkAndAlerts() {
        networkMonitor.addListener { online ->
            if (online) {
                offlineBanner.visibility = View.GONE
                loadLiveCrowdHeatmap()
                loadFestivalAlerts()
            } else {
                offlineBanner.visibility = View.VISIBLE
            }
        }
        loadFestivalAlerts()
    }

    private fun loadFestivalAlerts() {
        alerts.fetchActiveAlerts { alertList ->
            if (alertList.isNotEmpty()) {
                val topAlert = alertList.first()
                festivalAlertCard.visibility = View.VISIBLE
                val agoText = ago(topAlert.timestamp)
                festivalAlertText.text = "${topAlert.title}: ${topAlert.message} • Updated $agoText"
            } else {
                festivalAlertCard.visibility = View.GONE
            }
        }
    }

    private fun loadLiveCrowdHeatmap() {
        crowd.fetchAllRecentCrowds { crowds ->
            liveCrowdMap = crowds
            if (crowds.isNotEmpty()) {
                crowdLegendText.text = "Crowd (${crowds.size} active)"
            } else {
                crowdLegendText.text = "Crowd"
            }
            markerBitmapCache.clear()
            if (filterShowPandals) {
                applyCurrentFilters()
            }
        }
    }

    private fun updateFilterPillStyles() {
        val onPrimaryText = ContextCompat.getColor(this, R.color.on_primary)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)

        if (filterShowPandals) {
            pandalsFilterButton.setBackgroundResource(R.drawable.bg_filter_pill_pandal_active)
            pandalsFilterButton.backgroundTintList = null
            pandalsFilterButton.setTextColor(onPrimaryText)
            pandalsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.on_primary)
            pandalsFilterButton.strokeWidth = 0
        } else {
            pandalsFilterButton.setBackgroundResource(R.drawable.bg_filter_pill_inactive)
            pandalsFilterButton.backgroundTintList = null
            pandalsFilterButton.setTextColor(textPrimary)
            pandalsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.primary)
            pandalsFilterButton.strokeWidth = 0
        }

        if (filterShowMetro) {
            metroFilterButton.setBackgroundResource(R.drawable.bg_filter_pill_metro_active)
            metroFilterButton.backgroundTintList = null
            metroFilterButton.setTextColor(onPrimaryText)
            metroFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.on_primary)
            metroFilterButton.strokeWidth = 0
        } else {
            metroFilterButton.setBackgroundResource(R.drawable.bg_filter_pill_inactive)
            metroFilterButton.backgroundTintList = null
            metroFilterButton.setTextColor(textPrimary)
            metroFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.metro_icon)
            metroFilterButton.strokeWidth = 0
        }

        if (filterShowToilets) {
            toiletsFilterButton.setBackgroundResource(R.drawable.bg_filter_pill_toilets_active)
            toiletsFilterButton.backgroundTintList = null
            toiletsFilterButton.setTextColor(onPrimaryText)
            toiletsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.on_primary)
            toiletsFilterButton.strokeWidth = 0
        } else {
            toiletsFilterButton.setBackgroundResource(R.drawable.bg_filter_pill_inactive)
            toiletsFilterButton.backgroundTintList = null
            toiletsFilterButton.setTextColor(textPrimary)
            toiletsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.toilet_icon)
            toiletsFilterButton.strokeWidth = 0
        }

        zonesFilterButton.setBackgroundResource(R.drawable.bg_filter_pill_inactive)
        zonesFilterButton.backgroundTintList = null
        zonesFilterButton.setTextColor(textPrimary)
        zonesFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.secondary)
        zonesFilterButton.strokeWidth = 0

        updateFloatingBadges()
    }

    private fun fetchAndDisplayToilets() {
        val loc = location ?: Location("").apply {
            latitude = 22.5726
            longitude = 88.3639
        }
        Log.d(GooglePlacesRepository.DIAGNOSTIC_TAG, "2. Current GPS: latitude=${loc.latitude}, longitude=${loc.longitude}")
        places.fetchNearbyToilets(loc, GooglePlacesRepository.DEFAULT_SEARCH_RADIUS_METERS) { toiletsList, error ->
            if (error != null) {
                Log.e(GooglePlacesRepository.DIAGNOSTIC_TAG, "Places API Error: $error")
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            }
            if (toiletsList != null) {
                shownToilets = toiletsList
                Log.d(GooglePlacesRepository.DIAGNOSTIC_TAG, "11. Markers added count: ${toiletsList.size}")
                if (toiletsList.isEmpty()) {
                    Toast.makeText(this, "No toilets found nearby. Try expanding your search area.", Toast.LENGTH_SHORT).show()
                }
                if (filterShowToilets) {
                    applyCurrentFilters()
                }
            }
        }
    }

    private fun applyCurrentFilters() {
        val pandalList = if (filterShowPandals) {
            location?.let { pandals.nearby(it) } ?: pandals.all()
        } else {
            emptyList()
        }

        val metroList = if (filterShowMetro) {
            MetroStation.allStations()
        } else {
            emptyList()
        }

        val toiletList = if (filterShowToilets) {
            shownToilets
        } else {
            emptyList()
        }

        renderMarkers(pandalList, metroList, toiletList)
    }

    private fun setupHoppingTab() {
        hoppingRecyclerView.layoutManager = LinearLayoutManager(this)
        hoppingAdapter = HoppingAdapter(
            items = mutableListOf(),
            onRemove = { stop ->
                hopping.remove(stop.id)
                Toast.makeText(this, "Removed ${stop.name} from Hopping", Toast.LENGTH_SHORT).show()
            },
            onStopClick = { stop ->
                when (stop.type) {
                    StopType.PANDAL -> {
                        stop.pandalRef?.let { showDetail(it) }
                    }
                    StopType.METRO -> {
                        stop.metroRef?.let { station ->
                            selectTab(isMap = true)
                            focusMetro(station)
                            showMetroCard(station)
                        }
                    }
                    StopType.TOILET -> {
                        stop.toiletRef?.let { toilet ->
                            selectTab(isMap = true)
                            focusToilet(toilet)
                            showToiletCard(toilet)
                        }
                    }
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onItemMoved = { from, to ->
                hopping.move(from, to)
            }
        )
        hoppingRecyclerView.adapter = hoppingAdapter

        val touchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                rv: RecyclerView,
                src: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                hoppingAdapter.onItemMove(src.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper.attachToRecyclerView(hoppingRecyclerView)

        hopping.addListener {
            updateHoppingUI()
            updateHoppingBadge()
            currentSelectedMetro?.let { updateMetroCardHoppingState(it) }
            currentSelectedToilet?.let { updateToiletCardHoppingState(it) }
        }

        startHoppingButton.setOnClickListener {
            val plan = hopping.getPlan()
            if (plan.isNotEmpty()) {
                NavigationLauncher.startHopping(this, location, plan)
            } else {
                Toast.makeText(this, "Add places to your hopping plan first", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.hoppingExploreMapButton)?.setOnClickListener {
            selectTab(isMap = true)
        }

        updateHoppingUI()
        updateHoppingBadge()
    }

    private fun setupFloatingNav() {
        navMapPill.setOnClickListener {
            selectTab(isMap = true)
        }
        navHoppingPill.setOnClickListener {
            selectTab(isMap = false)
        }
        navMyPandalPill.setOnClickListener {
            showMyPandalSheet()
        }
        updateFloatingBadges()
    }

    private fun selectTab(isMap: Boolean) {
        if (isMap) {
            mapContainer.visibility = View.VISIBLE
            hoppingContainer.visibility = View.GONE

            navMapPill.setBackgroundResource(R.drawable.bg_nav_pill_active)
            navMapIcon.setColorFilter(ContextCompat.getColor(this, R.color.on_primary))
            navMapText.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

            navHoppingPill.setBackgroundColor(Color.TRANSPARENT)
            navHoppingIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navHoppingText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))

            navMyPandalPill.setBackgroundColor(Color.TRANSPARENT)
            navMyPandalIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navMyPandalText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))
        } else {
            mapContainer.visibility = View.GONE
            hoppingContainer.visibility = View.VISIBLE

            navHoppingPill.setBackgroundResource(R.drawable.bg_nav_pill_active)
            navHoppingIcon.setColorFilter(ContextCompat.getColor(this, R.color.on_primary))
            navHoppingText.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

            navMapPill.setBackgroundColor(Color.TRANSPARENT)
            navMapIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navMapText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))

            navMyPandalPill.setBackgroundColor(Color.TRANSPARENT)
            navMyPandalIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navMyPandalText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))

            updateHoppingUI()
        }
        updateFloatingBadges()
    }

    private fun updateFloatingBadges() {
        val hoppingCount = hopping.getPlanIds().size
        if (hoppingCount > 0) {
            navHoppingBadge.visibility = View.VISIBLE
            navHoppingBadge.text = hoppingCount.toString()
        } else {
            navHoppingBadge.visibility = View.GONE
        }
    }

    private fun updateHoppingBadge() {
        updateFloatingBadges()
    }

    private fun updateHoppingUI() {
        val plan = hopping.getPlan()
        if (plan.isEmpty()) {
            hoppingEmptyView.visibility = View.VISIBLE
            hoppingRecyclerView.visibility = View.GONE
            hoppingSummaryCard.visibility = View.GONE
            hoppingContextSummaryText.text = "0 stops"
        } else {
            hoppingEmptyView.visibility = View.GONE
            hoppingRecyclerView.visibility = View.VISIBLE
            hoppingSummaryCard.visibility = View.VISIBLE

            val countText = "${plan.size} ${if (plan.size == 1) "stop" else "stops"}"
            hoppingStopsCount.text = countText
            hoppingContextSummaryText.text = "$countText • Calculating route…"
            hoppingTotalDistance.text = "Calculating route…"
            hoppingEstimatedTime.text = "…"

            location?.let { origin ->
                routes.computeHoppingRoute(origin, plan) { routeResult ->
                    if (routeResult != null) {
                        val distStr = routeDistanceText(routeResult.totalDistanceMeters)
                        hoppingTotalDistance.text = distStr
                        hoppingEstimatedTime.text = routeResult.totalDurationFormatted
                        hoppingContextSummaryText.text = "$countText • $distStr"
                        hoppingAdapter.updateData(plan, routeResult.legDistances)
                    } else {
                        hoppingTotalDistance.text = "Route unavailable"
                        hoppingEstimatedTime.text = "—"
                        hoppingContextSummaryText.text = countText
                        hoppingAdapter.updateData(plan, emptyList())
                    }
                }
            } ?: run {
                hoppingTotalDistance.text = "Location needed"
                hoppingEstimatedTime.text = "—"
                hoppingContextSummaryText.text = "$countText • GPS needed"
                hoppingAdapter.updateData(plan, emptyList())
            }
        }
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        val currentDay = getTodayDateKey()
        if (currentDay != lastCrowdLoadedDay) {
            lastCrowdLoadedDay = currentDay
            loadLiveCrowdHeatmap()
            refreshCurrentDetailCrowd?.invoke()
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
        }
        try {
            registerReceiver(dateChangeReceiver, filter)
        } catch (_: Exception) {}

        // Check Location Permissions and Device Location Settings on Resume
        checkLocationSettingsAndStart(explicitUserAction = false)
    }
    override fun onPause() {
        mapView.onPause()
        try {
            unregisterReceiver(dateChangeReceiver)
        } catch (_: Exception) {}
        super.onPause()
    }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            isResolvingLocationSettings = false
            if (resultCode == android.app.Activity.RESULT_OK) {
                Log.d(TAG, "Location settings resolution successful. Starting location updates.")
                requestNearby()
                dismissLocationOffBanner()
                if (filterShowToilets) {
                    fetchAndDisplayToilets()
                }
            } else {
                Log.w(TAG, "Location settings resolution cancelled or rejected by user.")
                showLocationOffBanner()
            }
        }
    }

    // ────────────────────────────────────────────────────────
    //  Location Services & SettingsClient
    // ────────────────────────────────────────────────────────

    private fun checkLocationSettingsAndStart(
        explicitUserAction: Boolean = false,
        onSuccess: (() -> Unit)? = null
    ) {
        if (!hasLocation()) {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            Log.d(LOCATION_TAG, "permission before request = NOT_GRANTED (fine=$hasFine, coarse=$hasCoarse)")

            if (!hasRequestedLocationPermission) {
                // First launch / fresh install: request runtime permission immediately (shows native system dialog)
                Log.d(LOCATION_TAG, "requesting runtime location permission")
                requestLocationPermission(isExplicitUserAction = explicitUserAction)
            } else if (isLocationPermissionPermanentlyDenied()) {
                Log.d(LOCATION_TAG, "permission permanently denied -> showing Settings UI")
                showPermanentPermissionDeniedDialog()
            } else {
                Log.d(LOCATION_TAG, "permission temporarily denied -> showing Try Again UI")
                showLocationTryAgainBanner()
            }
            return
        }

        Log.d(LOCATION_TAG, "permission granted")
        Log.d(LOCATION_TAG, "checking device location services")

        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateDistanceMeters(10f)
            .build()

        val builder = com.google.android.gms.location.LocationSettingsRequest.Builder()
            .addLocationRequest(request)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(this)
        client.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                Log.d(LOCATION_TAG, "location services are enabled")
                isResolvingLocationSettings = false
                requestNearby()
                dismissLocationOffBanner()
                if (filterShowToilets) {
                    fetchAndDisplayToilets()
                }
                onSuccess?.invoke()
            }
            .addOnFailureListener { exception ->
                if (exception is com.google.android.gms.common.api.ResolvableApiException) {
                    Log.d(LOCATION_TAG, "location services disabled; SettingsClient resolution required")
                    val now = System.currentTimeMillis()
                    if (explicitUserAction || (!isResolvingLocationSettings && (now - lastLocationPromptTime > 15_000L))) {
                        lastLocationPromptTime = now
                        isResolvingLocationSettings = true
                        try {
                            exception.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)
                        } catch (sendEx: android.content.IntentSender.SendIntentException) {
                            isResolvingLocationSettings = false
                            showLocationOffBanner()
                        }
                    } else {
                        showLocationOffBanner()
                    }
                } else {
                    Log.w(LOCATION_TAG, "location services check failed: ${exception.message}")
                    showLocationOffBanner()
                }
            }
    }

    private fun isLocationPermissionPermanentlyDenied(): Boolean {
        val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return checkPermanentDenial(
            hasPermission = hasLocation(),
            hasRequestedBefore = hasRequestedLocationPermission,
            shouldShowRationale = shouldShowRationale
        )
    }

    private fun handleLocationPermissionDenied() {
        if (isLocationPermissionPermanentlyDenied()) {
            Log.d(LOCATION_TAG, "permission permanently denied after request -> showing Settings UI")
            showPermanentPermissionDeniedDialog()
        } else {
            Log.d(LOCATION_TAG, "permission temporarily denied after request -> showing Try Again UI")
            showLocationTryAgainBanner()
        }
    }

    private fun showPermanentPermissionDeniedDialog() {
        statusTitle.text = "Location permission is required"
        statusMessage.text = "Location permission was denied. Please open App Settings to grant location access."
        allowButton.text = "Open Settings"
        allowButton.visibility = View.VISIBLE
        allowButton.setOnClickListener {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
            startActivity(intent)
        }
        statusCard.visibility = View.VISIBLE
    }

    private fun showLocationTryAgainBanner() {
        statusTitle.text = getString(R.string.location_explanation_title)
        statusMessage.text = "Location is needed for nearby pandals, routes and navigation."
        allowButton.text = "Try Again"
        allowButton.visibility = View.VISIBLE
        allowButton.setOnClickListener {
            requestLocationPermission(isExplicitUserAction = true)
        }
        statusCard.visibility = View.VISIBLE
        renderMarkers(pandals.all(), emptyList())
    }

    private fun showLocationOffBanner() {
        statusTitle.text = "Location is turned off"
        statusMessage.text = "Location is off. Turn on Location to use nearby features."
        allowButton.text = "Turn On"
        allowButton.visibility = View.VISIBLE
        allowButton.setOnClickListener {
            checkLocationSettingsAndStart(explicitUserAction = true)
        }
        statusCard.visibility = View.VISIBLE
    }

    private fun dismissLocationOffBanner() {
        val title = statusTitle.text?.toString().orEmpty()
        val msg = statusMessage.text?.toString().orEmpty()
        if (title.contains("Location is turned off", ignoreCase = true) ||
            title.contains("Location permission is required", ignoreCase = true) ||
            title.contains("Find pandals around you", ignoreCase = true) ||
            msg.contains("Location is needed", ignoreCase = true) ||
            msg.contains("Location permission was denied", ignoreCase = true)
        ) {
            statusCard.visibility = View.GONE
        }
    }

    private fun dismissLocationPermissionBanners() {
        dismissLocationOffBanner()
    }

    private fun showLocationExplanation() {
        if (hasLocation()) {
            checkLocationSettingsAndStart(explicitUserAction = false)
        } else {
            showLocationTryAgainBanner()
        }
    }

    private fun requestLocationPermission(isExplicitUserAction: Boolean = false) {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasPerm = fineGranted || coarseGranted

        Log.d(LOCATION_TAG, "permission state before request = ${if (hasPerm) "GRANTED" else "NOT_GRANTED"} (fine=$fineGranted, coarse=$coarseGranted)")

        if (hasPerm) {
            dismissLocationPermissionBanners()
            checkLocationSettingsAndStart(explicitUserAction = isExplicitUserAction)
            return
        }

        if (isLocationPermissionPermanentlyDenied()) {
            Log.d(LOCATION_TAG, "permission is permanently denied -> showing Settings UI")
            showPermanentPermissionDeniedDialog()
            return
        }

        Log.d(LOCATION_TAG, "requesting runtime location permission")
        hasRequestedLocationPermission = true
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == REQUEST_LOCATION) {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val anyGranted = hasFine || hasCoarse
            Log.d(LOCATION_TAG, "onRequestPermissionsResult: fine=$hasFine, coarse=$hasCoarse, anyGranted=$anyGranted")
            if (anyGranted) {
                dismissLocationPermissionBanners()
                checkLocationSettingsAndStart(explicitUserAction = true)
            } else {
                handleLocationPermissionDenied()
            }
        }
    }

    private fun requestNearby() {
        if (!hasLocation()) return showLocationExplanation()
        allowButton.visibility = View.GONE
        statusTitle.text = getString(R.string.location_finding)
        statusMessage.text = getString(R.string.location_getting)

        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateDistanceMeters(10f)
            .build()

        if (locationCallback == null) {
            locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    val found = result.lastLocation ?: return
                    location = found

                    if (filterShowPandals) {
                        val nearby = pandals.nearby(found)
                        val within3km = nearby.filter { it.distanceMeters <= 3000f }
                        if (within3km.isEmpty()) {
                            statusTitle.text = getString(R.string.location_none_nearby)
                            statusMessage.text = getString(R.string.location_none_nearby_body, pandals.all().size)
                        } else if (within3km.size == 1) {
                            statusTitle.text = getString(R.string.location_found_one)
                            statusMessage.text = getString(R.string.location_tap)
                        } else {
                            statusTitle.text = getString(R.string.location_found, within3km.size)
                            statusMessage.text = getString(R.string.location_tap)
                        }
                        if (metroCard.visibility != View.VISIBLE && toiletCard.visibility != View.VISIBLE) {
                            statusCard.visibility = View.VISIBLE
                        }
                    }

                    applyCurrentFilters()

                    if (map?.cameraPosition?.zoom ?: 0.0 < 10.0 && isValidCoordinate(found.latitude, found.longitude)) {
                        map?.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(found.latitude, found.longitude))
                            .zoom(12.5)
                            .build()
                    }

                    // Check passport proximity
                    val nearbyUnvisited = passport.findUnvisitedNearbyPandal(found, pandals.all())
                    if (nearbyUnvisited != null) {
                        eligibility.markVisit(nearbyUnvisited.id)
                    }

                    if (hoppingContainer.visibility == View.VISIBLE) {
                        updateHoppingUI()
                    }

                    currentSelectedMetro?.let { showMetroCard(it) }
                    currentSelectedToilet?.let { showToiletCard(it) }
                }
            }
            try {
                LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(
                    request,
                    locationCallback!!,
                    android.os.Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                statusTitle.text = getString(R.string.location_unavailable)
                statusMessage.text = getString(R.string.location_enable_gps)
            }
        }
    }

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ────────────────────────────────────────────────────────
    //  Map rendering (Custom PandalFinder markers + Crowd)
    // ────────────────────────────────────────────────────────

    private fun renderMarkers(
        pandalList: List<Pandal>,
        stationList: List<MetroStation>,
        toiletList: List<PublicToilet> = emptyList()
    ) {
        shownPandals = pandalList
        shownStations = stationList
        shownToilets = toiletList

        map?.let { readyMap ->
            readyMap.clear()
            val icons = IconFactory.getInstance(this)

            // User Location Marker
            location?.let {
                if (isValidCoordinate(it.latitude, it.longitude)) {
                    readyMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(it.latitude, it.longitude))
                            .icon(icons.fromBitmap(userMarkerBitmap()))
                    )
                }
            }

            // Pandal Markers with Custom Silhouette & Crowd Halo
            if (pandalList.isNotEmpty()) {
                pandalList.forEach { pandal ->
                    if (isValidCoordinate(pandal.latitude, pandal.longitude)) {
                        val isSelected = selectedMarkerPosition?.let {
                            it.latitude == pandal.latitude && it.longitude == pandal.longitude
                        } ?: false
                        val crowdLevel = liveCrowdMap[pandal.id]
                        val iconBmp = pandalMarkerBitmap(isSelected, crowdLevel)
                        readyMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(pandal.latitude, pandal.longitude))
                                .icon(icons.fromBitmap(iconBmp))
                        )
                    }
                }
            }

            // Metro Station Markers
            if (stationList.isNotEmpty()) {
                stationList.forEach { station ->
                    if (isValidCoordinate(station.latitude, station.longitude)) {
                        val isSelected = selectedMarkerPosition?.let {
                            it.latitude == station.latitude && it.longitude == station.longitude
                        } ?: false
                        val iconBmp = metroMarkerBitmap(station, isSelected)
                        readyMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(station.latitude, station.longitude))
                                .icon(icons.fromBitmap(iconBmp))
                        )
                    }
                }
            }

            // Public Toilet Markers
            if (toiletList.isNotEmpty()) {
                toiletList.forEach { toilet ->
                    if (isValidCoordinate(toilet.latitude, toilet.longitude)) {
                        val isSelected = selectedMarkerPosition?.let {
                            it.latitude == toilet.latitude && it.longitude == toilet.longitude
                        } ?: false
                        val iconBmp = toiletMarkerBitmap(isSelected)
                        readyMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(toilet.latitude, toilet.longitude))
                                .icon(icons.fromBitmap(iconBmp))
                        )
                    }
                }
            }
        }
    }

    private fun focusPandal(pandal: Pandal) {
        if (!isValidCoordinate(pandal.latitude, pandal.longitude)) return
        selectedMarkerPosition = LatLng(pandal.latitude, pandal.longitude)
        if (!filterShowPandals) {
            filterShowPandals = true
            updateFilterPillStyles()
        }
        applyCurrentFilters()
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(pandal.latitude, pandal.longitude))
            .zoom(15.5)
            .build()
    }

    private fun focusMetro(station: MetroStation) {
        if (!isValidCoordinate(station.latitude, station.longitude)) return
        selectedMarkerPosition = LatLng(station.latitude, station.longitude)
        if (!filterShowMetro) {
            filterShowMetro = true
            updateFilterPillStyles()
        }
        applyCurrentFilters()
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(station.latitude, station.longitude))
            .zoom(15.5)
            .build()
    }

    private fun focusToilet(toilet: PublicToilet) {
        if (!isValidCoordinate(toilet.latitude, toilet.longitude)) return
        selectedMarkerPosition = LatLng(toilet.latitude, toilet.longitude)
        if (!filterShowToilets) {
            filterShowToilets = true
            updateFilterPillStyles()
        }
        applyCurrentFilters()
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(toilet.latitude, toilet.longitude))
            .zoom(15.5)
            .build()
    }

    // ────────────────────────────────────────────────────────
    //  Marker Bitmaps (Part 1: Custom Vector Silhouettes)
    // ────────────────────────────────────────────────────────

    /**
     * Custom PandalFinder Pandal Marker:
     * Stylized Durga Puja pandal/temple silhouette inside a crisp teardrop map-pin.
     * Primary #E52B00 with gold (#FBC222) and warm cream (#FFF5E3) internal temple architecture.
     * Subtle crowd halo when recent crowd reports exist.
     */
    private fun pandalMarkerBitmap(selected: Boolean = false, crowdLevel: Int? = null): Bitmap {
        val cacheKey = "pandal:${selected}:${crowdLevel ?: 0}"
        return markerBitmapCache.getOrPut(cacheKey) {
            val scale = if (selected) 1.25f else 1.0f
            val w = (dp(38) * scale).toInt()
            val h = (dp(48) * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val cx = w / 2f
                val headR = (w / 2f) - dp(3) * scale

                // Crowd indicator halo if recent report exists (Part 8)
                if (crowdLevel != null && crowdLevel in 1..10) {
                    val haloColor = when (crowdLevel) {
                        in 1..3 -> Color.parseColor("#45A701")
                        in 4..6 -> Color.parseColor("#FBC222")
                        in 7..8 -> Color.parseColor("#F97E04")
                        else -> Color.parseColor("#E52B00")
                    }
                    paint.color = haloColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(3) * scale
                    drawCircle(cx, cx, headR + dp(1.5f), paint)
                    paint.style = Paint.Style.FILL
                }

                if (selected) {
                    // Outer gold glowing halo for selected state
                    paint.color = Color.parseColor("#80FBC222")
                    drawCircle(cx, cx, headR + dp(3), paint)
                }

                // Primary Sindoor Red Pin Body
                paint.color = Color.parseColor("#E52B00")
                drawCircle(cx, cx, headR, paint)

                val pinTip = Path().apply {
                    moveTo(cx - (dp(11) * scale), cx + (dp(4) * scale))
                    lineTo(cx, h.toFloat() - (dp(2) * scale))
                    lineTo(cx + (dp(11) * scale), cx + (dp(4) * scale))
                    close()
                }
                drawPath(pinTip, paint)

                // Gold Accent Inner Ring
                paint.color = Color.parseColor("#FBC222")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.5f) * scale
                drawCircle(cx, cx, headR * 0.72f, paint)
                paint.style = Paint.Style.FILL

                // Stylized Durga Puja Temple Silhouette in Cream & Gold
                paint.color = Color.parseColor("#FFF5E3")

                // Temple Dome / Kalash
                val templePath = Path().apply {
                    // Kalash finial top
                    moveTo(cx, cx - (dp(9) * scale))
                    lineTo(cx - (dp(4) * scale), cx - (dp(3) * scale))
                    lineTo(cx + (dp(4) * scale), cx - (dp(3) * scale))
                    close()

                    // Temple Tier
                    moveTo(cx - (dp(6) * scale), cx - (dp(2.5f) * scale))
                    lineTo(cx + (dp(6) * scale), cx - (dp(2.5f) * scale))
                    lineTo(cx + (dp(7) * scale), cx + (dp(1.5f) * scale))
                    lineTo(cx - (dp(7) * scale), cx + (dp(1.5f) * scale))
                    close()

                    // Base Sanctum
                    moveTo(cx - (dp(6.5f) * scale), cx + (dp(2.5f) * scale))
                    lineTo(cx + (dp(6.5f) * scale), cx + (dp(2.5f) * scale))
                    lineTo(cx + (dp(6.5f) * scale), cx + (dp(7) * scale))
                    lineTo(cx - (dp(6.5f) * scale), cx + (dp(7) * scale))
                    close()
                }
                drawPath(templePath, paint)

                // Inner Sanctum Arch (Sindoor Red cut)
                paint.color = Color.parseColor("#E52B00")
                drawRoundRect(
                    RectF(
                        cx - (dp(2.5f) * scale),
                        cx + (dp(3.5f) * scale),
                        cx + (dp(2.5f) * scale),
                        cx + (dp(7) * scale)
                    ),
                    dp(2) * scale,
                    dp(2) * scale,
                    paint
                )
            }
            bitmap
        }
    }

    /**
     * Dedicated Metro Marker:
     * Clean metro train silhouette inside a rounded/circular pin in line/green theme.
     */
    private fun metroMarkerBitmap(station: MetroStation, selected: Boolean = false): Bitmap {
        val cacheKey = "metro:${station.lineColor}:${selected}"
        return markerBitmapCache.getOrPut(cacheKey) {
            val scale = if (selected) 1.25f else 1.0f
            val w = (dp(36) * scale).toInt()
            val h = (dp(46) * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val cx = w / 2f
                val headR = (w / 2f) - dp(2.5f) * scale
                val color = station.lineColor

                if (selected) {
                    paint.color = Color.parseColor("#6645A701")
                    drawCircle(cx, cx, headR + dp(3), paint)
                }

                // Line Color Pin Body
                paint.color = color
                drawCircle(cx, cx, headR, paint)

                val pinTip = Path().apply {
                    moveTo(cx - (dp(10) * scale), cx + (dp(4) * scale))
                    lineTo(cx, h.toFloat() - (dp(2) * scale))
                    lineTo(cx + (dp(10) * scale), cx + (dp(4) * scale))
                    close()
                }
                drawPath(pinTip, paint)

                // Clean White Circle Center
                paint.color = Color.WHITE
                drawCircle(cx, cx, headR * 0.65f, paint)

                // Train Silhouette
                paint.color = color
                val trainRect = RectF(
                    cx - (dp(5) * scale),
                    cx - (dp(6) * scale),
                    cx + (dp(5) * scale),
                    cx + (dp(4.5f) * scale)
                )
                drawRoundRect(trainRect, dp(2) * scale, dp(2) * scale, paint)

                // Train Window Cutout
                paint.color = Color.WHITE
                drawRoundRect(
                    RectF(
                        cx - (dp(3.5f) * scale),
                        cx - (dp(4.5f) * scale),
                        cx + (dp(3.5f) * scale),
                        cx - (dp(1) * scale)
                    ),
                    dp(1) * scale,
                    dp(1) * scale,
                    paint
                )

                // Headlights
                drawCircle(cx - (dp(2.5f) * scale), cx + (dp(2) * scale), dp(0.9f) * scale, paint)
                drawCircle(cx + (dp(2.5f) * scale), cx + (dp(2) * scale), dp(0.9f) * scale, paint)
            }
            bitmap
        }
    }

    /**
     * Dedicated Public Toilet / Restroom Marker:
     * Clean restroom silhouette inside a teal (#0D9488) pin.
     */
    private fun toiletMarkerBitmap(selected: Boolean = false): Bitmap {
        val cacheKey = "toilet:${selected}"
        return markerBitmapCache.getOrPut(cacheKey) {
            val scale = if (selected) 1.25f else 1.0f
            val w = (dp(36) * scale).toInt()
            val h = (dp(46) * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val cx = w / 2f
                val headR = (w / 2f) - dp(2.5f) * scale
                val tealColor = Color.parseColor("#0D9488")

                if (selected) {
                    paint.color = Color.parseColor("#660D9488")
                    drawCircle(cx, cx, headR + dp(3), paint)
                }

                // Teal Pin Body
                paint.color = tealColor
                drawCircle(cx, cx, headR, paint)

                val pinTip = Path().apply {
                    moveTo(cx - (dp(10) * scale), cx + (dp(4) * scale))
                    lineTo(cx, h.toFloat() - (dp(2) * scale))
                    lineTo(cx + (dp(10) * scale), cx + (dp(4) * scale))
                    close()
                }
                drawPath(pinTip, paint)

                // Clean White Circle Center
                paint.color = Color.WHITE
                drawCircle(cx, cx, headR * 0.65f, paint)

                // Restroom Figures Silhouette
                paint.color = tealColor

                // Man head & body
                drawCircle(cx - (dp(3) * scale), cx - (dp(4) * scale), dp(1.5f) * scale, paint)
                drawRoundRect(
                    RectF(
                        cx - (dp(4.5f) * scale),
                        cx - (dp(2) * scale),
                        cx - (dp(1.5f) * scale),
                        cx + (dp(4.5f) * scale)
                    ),
                    dp(1) * scale,
                    dp(1) * scale,
                    paint
                )

                // Woman head & body (dress)
                drawCircle(cx + (dp(3) * scale), cx - (dp(4) * scale), dp(1.5f) * scale, paint)
                val dressPath = Path().apply {
                    moveTo(cx + (dp(1.8f) * scale), cx - (dp(2) * scale))
                    lineTo(cx + (dp(4.2f) * scale), cx - (dp(2) * scale))
                    lineTo(cx + (dp(5.2f) * scale), cx + (dp(4.5f) * scale))
                    lineTo(cx + (dp(0.8f) * scale), cx + (dp(4.5f) * scale))
                    close()
                }
                drawPath(dressPath, paint)
            }
            bitmap
        }
    }

    private fun userMarkerBitmap(): Bitmap {
        val size = dp(28)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = size / 2f
            paint.color = Color.WHITE
            drawCircle(cx, cx, cx - dp(1), paint)
            paint.color = Color.rgb(21, 101, 192)
            drawCircle(cx, cx, cx - dp(4), paint)
        }
        return bitmap
    }

    // ────────────────────────────────────────────────────────
    //  Compact Floating Metro Card
    // ────────────────────────────────────────────────────────

    private fun showMetroCard(station: MetroStation) {
        currentSelectedMetro = station
        currentSelectedToilet = null
        statusCard.visibility = View.GONE
        toiletCard.visibility = View.GONE
        metroCard.visibility = View.VISIBLE

        metroCardIcon.setColorFilter(station.lineColor)
        metroCardIconContainer.backgroundTintList = ColorStateList.valueOf(station.lineBadgeBgColor)
        metroCardName.text = "${station.name} Metro"

        if (location != null && isValidCoordinate(location!!.latitude, location!!.longitude)) {
            val sLoc = Location("").apply {
                latitude = station.latitude
                longitude = station.longitude
            }
            val geodesicDistance = location!!.distanceTo(sLoc)
            val formattedGeodesic = distanceShort(geodesicDistance)
            metroCardDistance.text = "$formattedGeodesic from you • ${station.line}"

            routes.routesToStation(location!!, station) { routesData ->
                if (currentSelectedMetro?.name == station.name && routesData?.drive != null) {
                    val roadDist = routeDistanceText(routesData.drive.distanceMeters)
                    metroCardDistance.text = "$roadDist from you • ${station.line}"
                }
            }
        } else {
            metroCardDistance.text = "${station.line} • Location needed for distance"
        }

        updateMetroCardHoppingState(station)

        metroAddToHoppingCard.setOnClickListener {
            val inPlan = hopping.isMetroInPlan(station)
            if (inPlan) {
                hopping.removeMetro(station)
                Toast.makeText(this, "Removed ${station.name} Metro from Hopping", Toast.LENGTH_SHORT).show()
            } else {
                val added = hopping.addMetro(station)
                if (added) {
                    Toast.makeText(this, "Added ${station.name} Metro to Hopping", Toast.LENGTH_SHORT).show()
                }
            }
            updateMetroCardHoppingState(station)
        }

        metroNavigateButton.setOnClickListener {
            NavigationLauncher.openMetroRoute(this, station)
        }
    }

    private fun updateMetroCardHoppingState(station: MetroStation) {
        val inPlan = hopping.isMetroInPlan(station)
        if (inPlan) {
            metroHoppingActionIcon.setImageResource(R.drawable.ic_check)
            metroHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            metroAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
            metroAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
        } else {
            metroHoppingActionIcon.setImageResource(R.drawable.ic_add)
            metroHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            metroAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            metroAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.outline)
        }
    }

    // ────────────────────────────────────────────────────────
    //  Compact Floating Toilet Card
    // ────────────────────────────────────────────────────────

    private fun showToiletCard(toilet: PublicToilet) {
        currentSelectedToilet = toilet
        currentSelectedMetro = null
        statusCard.visibility = View.GONE
        metroCard.visibility = View.GONE
        toiletCard.visibility = View.VISIBLE

        toiletCardName.text = toilet.name

        if (location != null && isValidCoordinate(location!!.latitude, location!!.longitude)) {
            val tLoc = Location("").apply {
                latitude = toilet.latitude
                longitude = toilet.longitude
            }
            val geodesicDistance = location!!.distanceTo(tLoc)
            val formattedGeodesic = distanceShort(geodesicDistance)
            toiletCardDistance.text = "$formattedGeodesic from you • ${toilet.address}"

            routes.routesToCoordinates(location!!, "toilet:${toilet.id}", toilet.latitude, toilet.longitude) { routesData ->
                if (currentSelectedToilet?.id == toilet.id && routesData?.walk != null) {
                    val roadDist = routeDistanceText(routesData.walk.distanceMeters)
                    val walkTime = OpenRouteServiceProvider.formatDuration(routesData.walk.durationSeconds)
                    toiletCardDistance.text = "$roadDist ($walkTime walk) • ${toilet.address}"
                }
            }
        } else {
            toiletCardDistance.text = toilet.address
        }

        updateToiletCardHoppingState(toilet)

        toiletAddToHoppingCard.setOnClickListener {
            val inPlan = hopping.isToiletInPlan(toilet)
            if (inPlan) {
                hopping.removeToilet(toilet)
                Toast.makeText(this, "Removed ${toilet.name} from Hopping", Toast.LENGTH_SHORT).show()
            } else {
                val added = hopping.addToilet(toilet)
                if (added) {
                    Toast.makeText(this, "Added ${toilet.name} to Hopping", Toast.LENGTH_SHORT).show()
                }
            }
            updateToiletCardHoppingState(toilet)
        }

        toiletNavigateButton.setOnClickListener {
            val uri = android.net.Uri.parse("geo:0,0?q=${toilet.latitude},${toilet.longitude}(${android.net.Uri.encode(toilet.name)})")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            }
        }
    }

    private fun updateToiletCardHoppingState(toilet: PublicToilet) {
        val inPlan = hopping.isToiletInPlan(toilet)
        if (inPlan) {
            toiletHoppingActionIcon.setImageResource(R.drawable.ic_check)
            toiletHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            toiletAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
            toiletAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
        } else {
            toiletHoppingActionIcon.setImageResource(R.drawable.ic_add)
            toiletHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            toiletAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            toiletAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.outline)
        }
    }

    // ────────────────────────────────────────────────────────
    //  Pandal Detail Bottom Sheet (Enhanced)
    // ────────────────────────────────────────────────────────

    private fun showDetail(pandal: Pandal) {
        val item = location?.let { pandal.withDistanceFrom(it) } ?: pandal
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_pandal_detail, null)
        sheet.setContentView(view)

        currentDetailPandal = pandal
        currentDetailSheetView = view

        // ── Pandal info ──
        view.findViewById<TextView>(R.id.detailName).text = item.name
        val detailArea = view.findViewById<TextView>(R.id.detailArea)
        detailArea.text = "${item.area}    —"
        val mainDistance = view.findViewById<TextView>(R.id.detailDistance)
        mainDistance.text = if (location != null) "Finding road route…" else "Acquiring GPS location…"

        view.findViewById<View>(R.id.closeSheet).setOnClickListener {
            sheet.dismiss()
        }

        // ── Save / Favorite Action Button (Part 6) ──
        val favoriteButton = view.findViewById<MaterialCardView>(R.id.favoriteButton)
        val favoriteIcon = view.findViewById<ImageView>(R.id.favoriteIcon)
        val favoriteText = view.findViewById<TextView>(R.id.favoriteText)

        fun updateFavoriteState() {
            val isFav = favorites.isFavorite(item.id)
            if (isFav) {
                favoriteIcon.setImageResource(R.drawable.ic_heart_filled)
                favoriteIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                favoriteText.text = "Saved"
                favoriteButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
                favoriteButton.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
            } else {
                favoriteIcon.setImageResource(R.drawable.ic_heart_outline)
                favoriteIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                favoriteText.text = "Save"
                favoriteButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                favoriteButton.strokeColor = ContextCompat.getColor(this, R.color.outline)
            }
        }
        updateFavoriteState()

        favoriteButton.setOnClickListener {
            val nowFav = favorites.toggleFavorite(item.id)
            val scaleX = ObjectAnimator.ofFloat(favoriteIcon, "scaleX", 1.0f, 1.35f, 1.0f)
            val scaleY = ObjectAnimator.ofFloat(favoriteIcon, "scaleY", 1.0f, 1.35f, 1.0f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 300
                start()
            }
            updateFavoriteState()
            updateFilterPillStyles()
            Toast.makeText(this, if (nowFav) "Saved to your favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show()
        }

        // ── Passport / Visited Action Button (Part 7) ──
        val passportButton = view.findViewById<MaterialCardView>(R.id.passportButton)
        val passportIcon = view.findViewById<ImageView>(R.id.passportIcon)
        val passportText = view.findViewById<TextView>(R.id.passportText)

        fun updatePassportState() {
            val isVis = passport.isVisited(item.id)
            if (isVis) {
                passportIcon.setImageResource(R.drawable.ic_check_circle)
                passportIcon.setColorFilter(ContextCompat.getColor(this, R.color.metro_green))
                passportText.text = "Visited"
                passportButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.metro_surface))
                passportButton.strokeColor = ContextCompat.getColor(this, R.color.metro_outline)
            } else {
                passportIcon.setImageResource(R.drawable.ic_passport)
                passportIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                passportText.text = "Mark Visited"
                passportButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                passportButton.strokeColor = ContextCompat.getColor(this, R.color.outline)
            }
        }
        updatePassportState()

        passportButton.setOnClickListener {
            val isVis = passport.isVisited(item.id)
            if (isVis) {
                passport.removeVisited(item.id)
                Toast.makeText(this, "Visit removed", Toast.LENGTH_SHORT).show()
            } else {
                passport.markVisited(item)
                Toast.makeText(this, "Stamped in your Pandal Passport!", Toast.LENGTH_SHORT).show()
            }
            updatePassportState()
            updateFilterPillStyles()
        }

        // ── Top Hero Image Carousel (Above Pandal Name - Real Photos Only) ──
        val heroCard = view.findViewById<View>(R.id.heroCard)
        val heroViewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.heroViewPager)
        val heroPageIndicator = view.findViewById<TextView>(R.id.heroPageIndicator)
        val closeHeroBtn = view.findViewById<View>(R.id.closeSheetHero)
        val galleryCountBadge = view.findViewById<TextView>(R.id.galleryCountBadge)

        closeHeroBtn?.setOnClickListener { sheet.dismiss() }

        lateinit var heroAdapter: com.pandalfinder.ui.HeroPhotoAdapter
        heroAdapter = com.pandalfinder.ui.HeroPhotoAdapter(emptyList()) { photo ->
            showFullscreenPhotoViewer(heroAdapter.getPhotos(), heroViewPager.currentItem, item.name)
        }
        heroViewPager.adapter = heroAdapter

        val heroHandler = Handler(Looper.getMainLooper())
        var heroAutoScrollRunnable: Runnable? = null

        fun stopHeroAutoScroll() {
            heroAutoScrollRunnable?.let { heroHandler.removeCallbacks(it) }
            heroAutoScrollRunnable = null
        }

        fun scheduleHeroAutoScroll() {
            stopHeroAutoScroll()
            val count = heroAdapter.itemCount
            if (count <= 1) return

            val runnable = object : Runnable {
                override fun run() {
                    if (currentDetailPandal?.id == item.id && currentDetailSheetView === view && heroAdapter.itemCount > 1) {
                        val nextItem = (heroViewPager.currentItem + 1) % heroAdapter.itemCount
                        heroViewPager.setCurrentItem(nextItem, true)
                        heroHandler.postDelayed(this, 4500L)
                    }
                }
            }
            heroAutoScrollRunnable = runnable
            heroHandler.postDelayed(runnable, 4500L)
        }

        heroViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val count = heroAdapter.itemCount
                if (count > 0) {
                    heroPageIndicator.text = "${position + 1} / $count"
                    heroPageIndicator.visibility = if (count > 1) View.VISIBLE else View.GONE
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING) {
                    stopHeroAutoScroll()
                } else if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE) {
                    scheduleHeroAutoScroll()
                }
            }
        })

        // ── Community Photo Gallery & Separate Add Photo Card ──
        val photoRecycler = view.findViewById<RecyclerView>(R.id.pandalPhotosRecyclerView)
        val photoPlaceholder = view.findViewById<View>(R.id.pandalPhotoPlaceholder)
        val addPhotoCard = view.findViewById<MaterialCardView>(R.id.addPhotoCard)

        val photoAdapter = com.pandalfinder.ui.PhotoGalleryAdapter(emptyList()) { photo ->
            showFullPhotoDialog(photo, heroAdapter.getPhotos(), item.name)
        }
        photoRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        photoRecycler.adapter = photoAdapter

        val photoListener = photos.listenToPhotos(item.id) { photoList ->
            if (currentDetailPandal?.id == item.id && currentDetailSheetView === view) {
                heroAdapter.updatePhotos(photoList)
                photoAdapter.updatePhotos(photoList)

                if (photoList.isNotEmpty()) {
                    heroCard.visibility = View.VISIBLE
                    if (photoList.size > 1) {
                        heroPageIndicator.text = "1 / ${photoList.size}"
                        heroPageIndicator.visibility = View.VISIBLE
                        scheduleHeroAutoScroll()
                    } else {
                        heroPageIndicator.visibility = View.GONE
                        stopHeroAutoScroll()
                    }

                    photoRecycler.visibility = View.VISIBLE
                    photoPlaceholder.visibility = View.GONE
                    galleryCountBadge.visibility = View.VISIBLE
                    galleryCountBadge.text = if (photoList.size == 1) "1 photo" else "${photoList.size} photos"
                } else {
                    stopHeroAutoScroll()
                    heroCard.visibility = View.GONE
                    heroPageIndicator.visibility = View.GONE

                    photoRecycler.visibility = View.GONE
                    photoPlaceholder.visibility = View.VISIBLE
                    galleryCountBadge.visibility = View.GONE
                }
            }
        }

        addPhotoCard.setOnClickListener {
            showChoosePhotoSourceDialog(item)
        }

        // ── Optional Metadata Section (Part 13: Theme, Established, Known For) ──
        val infoSection = view.findViewById<LinearLayout>(R.id.pandalInfoSection)
        val infoTheme = view.findViewById<TextView>(R.id.infoTheme)
        val infoEst = view.findViewById<TextView>(R.id.infoEstablished)
        val infoKnown = view.findViewById<TextView>(R.id.infoKnownFor)

        var hasAnyInfo = false
        if (!item.theme.isNullOrBlank()) {
            infoTheme.visibility = View.VISIBLE
            infoTheme.text = "Theme: ${item.theme}"
            hasAnyInfo = true
        }
        if (item.establishedYear != null && item.establishedYear > 0) {
            infoEst.visibility = View.VISIBLE
            infoEst.text = "Established: ${item.establishedYear}"
            hasAnyInfo = true
        }
        if (!item.knownFor.isNullOrBlank()) {
            infoKnown.visibility = View.VISIBLE
            infoKnown.text = "Known for: ${item.knownFor}"
            hasAnyInfo = true
        }
        infoSection.visibility = if (hasAnyInfo) View.VISIBLE else View.GONE

        // ── Travel Mode Chips ──
        val walkDistance = view.findViewById<TextView>(R.id.timeWalk)
        val bikeDistance = view.findViewById<TextView>(R.id.timeBike)
        val carDistance = view.findViewById<TextView>(R.id.timeCar)
        walkDistance.text = "…"
        bikeDistance.text = "…"
        carDistance.text = "…"

        calculateDetailRoutes(location, item, view)

        // Diagnostic tap on road distance
        mainDistance.setOnLongClickListener {
            showDiagnosticDialog()
            true
        }
        mainDistance.setOnClickListener {
            if (routes.diagnosticState.lastHttpStatus != 200) {
                showDiagnosticDialog()
            }
        }

        // ── Weather (Authoritative Open-Meteo + Community Observation Layer + Refresh) ──
        val weatherStatus = view.findViewById<TextView>(R.id.weatherStatus)
        val weatherUpdated = view.findViewById<TextView>(R.id.weatherUpdated)
        val weatherCardIcon = view.findViewById<ImageView>(R.id.weatherCardIcon)
        val weatherRefreshBtn = view.findViewById<MaterialButton>(R.id.weatherRefreshButton)
        weatherStatus.text = getString(R.string.weather_checking)
        weatherUpdated.text = ""

        fun loadBaselineWeather() {
            weather.currentFor(item) { result ->
                if (currentDetailPandal?.id == item.id && currentDetailSheetView === view) {
                    if (result != null) {
                        weatherCardIcon.setImageResource(R.drawable.ic_weather)
                        weatherStatus.text = "${result.emoji} ${result.label}"
                        weatherUpdated.text = "Live weather · Updated ${ago(result.fetchedAt)}"
                    } else {
                        weatherCardIcon.setImageResource(R.drawable.ic_weather)
                        weatherStatus.text = getString(R.string.weather_no_data)
                        weatherUpdated.text = getString(R.string.weather_unavailable)
                    }
                }
            }
        }
        loadBaselineWeather()

        weatherRefreshBtn.setOnClickListener {
            animateButtonPress(weatherRefreshBtn)
            animateIconSpin(weatherCardIcon)
            loadBaselineWeather()
            Toast.makeText(this, "Refreshing weather…", Toast.LENGTH_SHORT).show()
        }

        // ── Real-time Community Weather Observation Sub-card ──
        val communityWeatherStatus = view.findViewById<TextView>(R.id.communityWeatherStatus)
        val communityWeatherIcon = view.findViewById<ImageView>(R.id.communityWeatherIcon)
        val btnUpdateWeather = view.findViewById<MaterialButton>(R.id.btnUpdateWeather)

        val weatherListener = weatherReports.listenToWeather(item.id) { report, count ->
            if (currentDetailPandal?.id == item.id && currentDetailSheetView === view) {
                if (report != null) {
                    val countSuffix = if (count > 1) " ($count community reports)" else ""
                    communityWeatherStatus.text = "${report.condition.label} · Reported ${ago(report.timestamp)}$countSuffix"
                    communityWeatherIcon.setImageResource(report.condition.iconRes)
                } else {
                    communityWeatherStatus.text = "No recent report"
                    communityWeatherIcon.setImageResource(R.drawable.ic_weather_rain)
                }
            }
        }

        btnUpdateWeather.setOnClickListener {
            animateButtonPress(btnUpdateWeather)
            showRainStatusDialog(item)
        }

        // ── Community Rating (1 to 5 Stars) Section ──
        val tvRatingCount = view.findViewById<TextView>(R.id.tvRatingCount)
        val tvRatingAvg = view.findViewById<TextView>(R.id.tvRatingAvg)
        val aggregateScoreContainer = view.findViewById<View>(R.id.aggregateScoreContainer)
        val tvUserRatingStatus = view.findViewById<TextView>(R.id.tvUserRatingStatus)
        val rateStars = listOf<ImageView>(
            view.findViewById(R.id.rateStar1),
            view.findViewById(R.id.rateStar2),
            view.findViewById(R.id.rateStar3),
            view.findViewById(R.id.rateStar4),
            view.findViewById(R.id.rateStar5)
        )

        fun updateUserStarsUI(rating: Int) {
            for (i in 0 until 5) {
                if (i < rating) {
                    rateStars[i].setImageResource(R.drawable.ic_star_filled)
                    rateStars[i].setColorFilter(ContextCompat.getColor(this, R.color.gold))
                } else {
                    rateStars[i].setImageResource(R.drawable.ic_star_outline)
                    rateStars[i].setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
                }
            }
            tvUserRatingStatus.text = "Your rating: $rating/5"
        }

        for (i in 0 until 5) {
            val starIndex = i + 1
            rateStars[i].setOnClickListener {
                animateButtonPress(rateStars[i])
                updateUserStarsUI(starIndex)
                tvUserRatingStatus.text = "Saving your rating…"
                ratingRepo.submitRating(item.id, starIndex) { success, err ->
                    if (success) {
                        tvUserRatingStatus.text = "Your rating: $starIndex/5"
                        Toast.makeText(this, "Rated $starIndex / 5 stars for ${item.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, err ?: "Couldn't save your rating. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val ratingListener = ratingRepo.listenToRatings(item.id) { userRating, avg, count ->
            if (currentDetailPandal?.id == item.id && currentDetailSheetView === view) {
                val formattedRating = RatingRepository.formatRatingDisplay(if (count > 0) avg else null)
                detailArea.text = "${item.area}    $formattedRating"

                if (userRating != null) {
                    updateUserStarsUI(userRating)
                }
                if (count > 0 && avg != null) {
                    aggregateScoreContainer.visibility = View.VISIBLE
                    tvRatingAvg.text = String.format(java.util.Locale.US, "%.1f", avg)
                    tvRatingCount.text = "$count ${if (count == 1) "rating" else "ratings"}"
                } else {
                    aggregateScoreContainer.visibility = View.GONE
                    tvRatingCount.text = "No ratings yet"
                }
            }
        }

        // ── Crowd (from Firestore realtime snapshot) & Best Time to Visit (Part 9) ──
        val crowdStatus = view.findViewById<TextView>(R.id.crowdStatus)
        val crowdLabel = view.findViewById<TextView>(R.id.crowdLabel)
        val crowdUpdated = view.findViewById<TextView>(R.id.crowdUpdated)
        val bestTimeSummary = view.findViewById<TextView>(R.id.bestTimeSummary)
        crowdStatus.text = getString(R.string.crowd_loading)
        crowdLabel.text = ""
        crowdUpdated.text = ""

        fun refreshCrowd() {
            crowd.load(item) { result, _ ->
                if (result?.level != null) {
                    crowdStatus.text = "${result.level} / 10"
                    crowdLabel.text = crowdLabelText(result.level)
                    val countText = if (result.reportCount == 1)
                        getString(R.string.crowd_report_count_one)
                    else
                        getString(R.string.crowd_report_count, result.reportCount)
                    val timeText = result.updatedAt?.let { "Updated ${ago(it)}" } ?: ""
                    crowdUpdated.text = "$countText · $timeText"
                } else {
                    crowdStatus.text = "No reports today"
                    crowdLabel.text = ""
                    crowdUpdated.text = "Be the first to report crowd level"
                }
            }

            crowd.getHistoricalCrowdByHour(item.id) { slots ->
                if (slots != null && slots.isNotEmpty()) {
                    val summaryText = slots.joinToString("  •  ") { "${it.hourLabel}: ${it.levelLabel}" }
                    bestTimeSummary.text = summaryText
                } else {
                    bestTimeSummary.text = "Not enough crowd data yet."
                }
            }
        }
        refreshCurrentDetailCrowd = { refreshCrowd() }
        refreshCrowd()

        val crowdListener = crowd.listenToPandalCrowd(item.id) { status ->
            if (currentDetailPandal?.id == item.id && currentDetailSheetView === view) {
                if (status.level != null) {
                    crowdStatus.text = "${status.level} / 10"
                    crowdLabel.text = crowdLabelText(status.level)
                    val countText = if (status.reportCount == 1) "1 report" else "${status.reportCount} reports"
                    val timeText = status.updatedAt?.let { "Updated ${ago(it)}" } ?: ""
                    crowdUpdated.text = "$countText · $timeText"
                } else {
                    crowdStatus.text = "No reports today"
                    crowdLabel.text = ""
                    crowdUpdated.text = "Be the first to report crowd level"
                }
            }
        }

        val updateCrowdBtn = view.findViewById<MaterialButton>(R.id.updateCrowdButton)
        updateCrowdBtn.setOnClickListener {
            animateButtonPress(updateCrowdBtn) {
                val userLoc = location
                if (userLoc == null || !isValidCoordinate(userLoc.latitude, userLoc.longitude)) {
                    Toast.makeText(this, "Acquiring GPS location... Please enable location to report crowd.", Toast.LENGTH_SHORT).show()
                    requestLocationPermission(isExplicitUserAction = true)
                    return@animateButtonPress
                }

                val pandalLoc = Location("").apply {
                    latitude = item.latitude
                    longitude = item.longitude
                }
                val distanceMeters = userLoc.distanceTo(pandalLoc)

                if (distanceMeters <= 500f) {
                    showCrowdUpdateSheet(item) { refreshCrowd() }
                } else {
                    val distStr = if (distanceMeters >= 1000f) "%.1f km".format(distanceMeters / 1000f) else "${distanceMeters.toInt()} m"
                    Toast.makeText(
                        this,
                        "Move within 500 m of this pandal to report crowd. (You're $distStr away)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // ── Nearest Metro (Part 14) ──
        val metroName = view.findViewById<TextView>(R.id.metroName)
        val metroWalkTime = view.findViewById<TextView>(R.id.metroWalkTime)
        val metroGate = view.findViewById<TextView>(R.id.metroGate)
        val metroNavigateBtn = view.findViewById<MaterialButton>(R.id.metroNavigateButton)
        val metroResult = metro.nearestTo(item)
        val mDistStr = distanceShort(metroResult.distanceMeters)
        metroName.text = metroResult.station.name
        metroWalkTime.visibility = View.VISIBLE
        metroWalkTime.text = "${metroResult.station.line} · $mDistStr"

        if (metroResult.station.gateNumber != null) {
            metroGate.visibility = View.VISIBLE
            metroGate.text = "Gate ${metroResult.station.gateNumber}"
        } else {
            metroGate.visibility = View.GONE
        }

        metroNavigateBtn.setOnClickListener {
            NavigationLauncher.openMetroRoute(this, metroResult.station)
        }

        // ── Compact Secondary Hopping Toggle Card ──
        val addToHoppingCard = view.findViewById<MaterialCardView>(R.id.addToHoppingCard)
        val hoppingActionIcon = view.findViewById<ImageView>(R.id.hoppingActionIcon)

        fun updateHoppingButtonState() {
            val inPlan = hopping.isPandalInPlan(item.id)
            if (inPlan) {
                hoppingActionIcon.setImageResource(R.drawable.ic_check)
                hoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                addToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
                addToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
            } else {
                hoppingActionIcon.setImageResource(R.drawable.ic_add)
                hoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                addToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                addToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.outline)
            }
        }
        updateHoppingButtonState()

        addToHoppingCard.setOnClickListener {
            val inPlan = hopping.isPandalInPlan(item.id)
            if (inPlan) {
                hopping.removePandal(item.id)
                Toast.makeText(this, "Removed from Hopping", Toast.LENGTH_SHORT).show()
            } else {
                hopping.addPandal(item)
                Toast.makeText(this, "Added to Hopping", Toast.LENGTH_SHORT).show()
            }
            updateHoppingButtonState()
        }

        // ── Dominant Primary Navigation Button ──
        view.findViewById<MaterialButton>(R.id.navigateButton).setOnClickListener {
            NavigationLauncher.open(this, item)
        }

        sheet.setOnDismissListener {
            stopHeroAutoScroll()
            crowdListener?.remove()
            photoListener?.remove()
            weatherListener?.remove()
            ratingListener?.remove()
            currentDetailPandal = null
            currentDetailSheetView = null
            refreshCurrentDetailCrowd = null
        }

        sheet.setOnShowListener {
            val bottomSheet = sheet.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                val displayMetrics = resources.displayMetrics
                val targetHeight = (displayMetrics.heightPixels * 0.90).toInt()
                bottomSheet.layoutParams.height = targetHeight
                bottomSheet.requestLayout()
                view.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                view.requestLayout()
                behavior.skipCollapsed = false
                behavior.isFitToContents = true
                behavior.peekHeight = (displayMetrics.heightPixels * 0.72).toInt()
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        sheet.show()
    }

    private fun calculateDetailRoutes(origin: Location?, item: Pandal, view: View) {
        val mainDistance = view.findViewById<TextView>(R.id.detailDistance)
        val walkDistance = view.findViewById<TextView>(R.id.timeWalk)
        val bikeDistance = view.findViewById<TextView>(R.id.timeBike)
        val carDistance = view.findViewById<TextView>(R.id.timeCar)

        Log.d(
            "PandalQuest-Location",
            "pandal name=${item.name} | pandal ID=${item.id} | canonical lat=${item.latitude} | canonical lng=${item.longitude} | current lat=${origin?.latitude} | current lng=${origin?.longitude} | route origin=(${origin?.latitude}, ${origin?.longitude}) | route destination=(${item.latitude}, ${item.longitude})"
        )

        if (origin == null) {
            mainDistance.text = "Acquiring GPS location…"
            walkDistance.text = "…"
            bikeDistance.text = "…"
            carDistance.text = "…"
            return
        }

        routes.routesFrom(origin, item) { routeData ->
            if (currentDetailPandal?.id != item.id || currentDetailSheetView !== view) return@routesFrom
            if (routeData == null) {
                mainDistance.text = getString(R.string.road_distance_unavailable)
                walkDistance.text = "—"
                bikeDistance.text = "—"
                carDistance.text = "—"
            } else {
                val primary = routeData.walk ?: routeData.drive ?: routeData.twoWheeler
                mainDistance.text = primary?.let {
                    val dist = routeDistanceText(it.distanceMeters)
                    if (it.durationSeconds > 0) "$dist • ${OpenRouteServiceProvider.formatDuration(it.durationSeconds)}" else dist
                } ?: getString(R.string.road_distance_unavailable)

                walkDistance.text = routeData.walk?.let { OpenRouteServiceProvider.formatDuration(it.durationSeconds) } ?: "—"
                bikeDistance.text = routeData.twoWheeler?.let { OpenRouteServiceProvider.formatDuration(it.durationSeconds) } ?: "—"
                carDistance.text = routeData.drive?.let { OpenRouteServiceProvider.formatDuration(it.durationSeconds) } ?: "—"
            }
        }
    }

    private fun showDiagnosticDialog() {
        val diag = routes.diagnosticState
        val view = layoutInflater.inflate(R.layout.dialog_routes_diagnostic, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)

        val googleStatus = if (diag.apiKeyConfigured) "Google: Set" else "Google: Missing"
        val orsStatus = if (diag.orsApiKeyConfigured) "ORS: Set" else "ORS: Missing"
        view.findViewById<TextView>(R.id.diagApiKeyStatus).text =
            "Keys: $googleStatus | $orsStatus"

        view.findViewById<TextView>(R.id.diagPackageName).text =
            "Provider: ${diag.lastProviderUsed} (Pkg: ${GoogleRoutesRepository.PACKAGE_NAME})"

        view.findViewById<TextView>(R.id.diagGpsStatus).text =
            if (location != null) "Current GPS: Acquired (${"%.5f".format(location!!.latitude)}, ${"%.5f".format(location!!.longitude)})"
            else "Current GPS: Acquiring / Not available"

        view.findViewById<TextView>(R.id.diagOriginCoords).text = "Origin: ${diag.lastOrigin.ifBlank { "None" }}"
        view.findViewById<TextView>(R.id.diagDestCoords).text = "Destination: ${diag.lastDestination.ifBlank { "None" }}"
        view.findViewById<TextView>(R.id.diagLastHttp).text = "Google HTTP: ${if (diag.lastHttpStatus > 0) "${diag.lastHttpStatus}" else "—"} | ORS HTTP: ${if (diag.orsLastHttpStatus > 0) "${diag.orsLastHttpStatus}" else "—"}"
        val lastErr = diag.lastErrorMessage.ifBlank { diag.orsLastErrorMessage.ifBlank { "OK" } }
        view.findViewById<TextView>(R.id.diagLastError).text = "Status: $lastErr"

        view.findViewById<View>(R.id.closeDiagnostic).setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun showCrowdUpdateSheet(pandal: Pandal, onUpdated: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.sheet_crowd_update, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.crowdUpdatePandalTitle).text = pandal.name
        val numberText = view.findViewById<TextView>(R.id.crowdSelectedNumber)
        val labelText = view.findViewById<TextView>(R.id.crowdSelectedLabel)
        val descText = view.findViewById<TextView>(R.id.crowdSelectedDesc)
        val submitBtn = view.findViewById<MaterialButton>(R.id.submitCrowdReportBtn)

        var selectedLevel = 5

        fun updateLevelDisplay(level: Int) {
            selectedLevel = level
            numberText.text = "$level / 10"
            val (lbl, colorHex) = crowd.getCrowdLabel(level)
            labelText.text = lbl
            labelText.setTextColor(Color.parseColor(colorHex))
            descText.text = when (level) {
                in 1..2 -> "No crowd, direct entry with zero waiting."
                in 3..4 -> "Light crowd, walking smoothly through pandal."
                in 5..6 -> "Moderate queue, 10–20 minute wait."
                in 7..8 -> "Very busy, packed entry queue (30–45 mins)."
                9 -> "Extremely busy, massive queue moving slowly."
                10 -> "Completely packed, heavy police barricades."
                else -> ""
            }
        }

        updateLevelDisplay(5)

        val buttons = listOf(
            view.findViewById<Button>(R.id.btnLevel1) to 1,
            view.findViewById<Button>(R.id.btnLevel2) to 2,
            view.findViewById<Button>(R.id.btnLevel3) to 3,
            view.findViewById<Button>(R.id.btnLevel4) to 4,
            view.findViewById<Button>(R.id.btnLevel5) to 5,
            view.findViewById<Button>(R.id.btnLevel6) to 6,
            view.findViewById<Button>(R.id.btnLevel7) to 7,
            view.findViewById<Button>(R.id.btnLevel8) to 8,
            view.findViewById<Button>(R.id.btnLevel9) to 9,
            view.findViewById<Button>(R.id.btnLevel10) to 10
        )

        buttons.forEach { (btn, lvl) ->
            btn.setOnClickListener {
                animateButtonPress(btn) {
                    updateLevelDisplay(lvl)
                }
            }
        }

        submitBtn.setOnClickListener {
            animateButtonPress(submitBtn) {
                submitBtn.isEnabled = false
                submitBtn.text = "Saving..."

                val devId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "device_user"
                authRepo.getOrCreateUid { uid ->
                    crowd.submit(pandal, selectedLevel, uid, devId, location) { error ->
                        mainHandler.post {
                            if (error != null) {
                                submitBtn.isEnabled = true
                                submitBtn.text = "Submit Crowd Report"
                                Log.e(TAG, "Crowd update failed: ${error.message}", error)
                                Toast.makeText(this, "Crowd update failed: ${error.localizedMessage ?: "Network or permission error"}", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Crowd updated: $selectedLevel/10", Toast.LENGTH_SHORT).show()
                                sheet.dismiss()
                                onUpdated()
                                loadLiveCrowdHeatmap()
                            }
                        }
                    }
                }
            }
        }

        view.findViewById<View>(R.id.closeCrowdUpdate).setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun showFullscreenPhotoViewer(
        photoList: List<com.pandalfinder.data.PandalPhoto>,
        initialPosition: Int = 0,
        pandalName: String? = null
    ) {
        if (photoList.isEmpty()) return

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = layoutInflater.inflate(R.layout.dialog_fullscreen_photo_viewer, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.black)

        val viewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.fullscreenViewPager)
        val topBar = view.findViewById<View>(R.id.fullscreenTopBar)
        val closeBtn = view.findViewById<View>(R.id.closeFullscreenBtn)
        val titleText = view.findViewById<TextView>(R.id.fullscreenPandalTitle)
        val subtitleText = view.findViewById<TextView>(R.id.fullscreenPhotoSubtitle)
        val counterText = view.findViewById<TextView>(R.id.fullscreenPhotoCounter)

        titleText.text = pandalName ?: "Pandal Photo"

        var isTopBarVisible = true
        val adapter = com.pandalfinder.ui.FullscreenPhotoViewerAdapter(photoList) {
            isTopBarVisible = !isTopBarVisible
            topBar.animate()
                .alpha(if (isTopBarVisible) 1f else 0f)
                .translationY(if (isTopBarVisible) 0f else -topBar.height.toFloat())
                .setDuration(180)
                .start()
        }

        viewPager.adapter = adapter
        viewPager.orientation = androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL

        fun updateHeader(position: Int) {
            if (position in photoList.indices) {
                val photo = photoList[position]
                subtitleText.text = if (photo.createdAt > 0) "Uploaded ${ago(photo.createdAt)}" else "Community photo"
                counterText.text = "${position + 1} / ${photoList.size}"
                counterText.visibility = if (photoList.size > 1) View.VISIBLE else View.GONE
            }
        }

        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateHeader(position)
            }
        })

        val safePosition = initialPosition.coerceIn(0, photoList.size - 1)
        viewPager.setCurrentItem(safePosition, false)
        updateHeader(safePosition)

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showFullPhotoDialog(
        photo: com.pandalfinder.data.PandalPhoto,
        photoList: List<com.pandalfinder.data.PandalPhoto> = listOf(photo),
        pandalName: String? = null
    ) {
        val view = layoutInflater.inflate(R.layout.dialog_photo_view, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(view)

        val timeText = view.findViewById<TextView>(R.id.photoUploadedTime)
        val fullImage = view.findViewById<ImageView>(R.id.fullPhotoImage)
        val progress = view.findViewById<ProgressBar>(R.id.fullPhotoProgress)
        val deleteBtn = view.findViewById<MaterialButton>(R.id.deletePhotoButton)
        val closeBtn = view.findViewById<View>(R.id.closePhotoView)

        timeText.text = if (photo.createdAt > 0) "Uploaded ${ago(photo.createdAt)}" else "Community photo"

        val cached = com.pandalfinder.ui.ImageCache[photo.downloadUrl]
        if (cached != null) {
            progress.visibility = View.GONE
            fullImage.setImageBitmap(cached)
        } else {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                val bmp = runCatching {
                    val stream = java.net.URL(photo.downloadUrl).openStream()
                    val decoded = BitmapFactory.decodeStream(stream)
                    stream.close()
                    decoded
                }.getOrNull()

                mainHandler.post {
                    progress.visibility = View.GONE
                    if (bmp != null) {
                        com.pandalfinder.ui.ImageCache[photo.downloadUrl] = bmp
                        fullImage.setImageBitmap(bmp)
                    }
                }
            }
        }

        fullImage.setOnClickListener {
            val idx = photoList.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
            showFullscreenPhotoViewer(photoList, idx, pandalName)
        }

        photos.currentUserId { currentUid ->
            val isOwner = photo.uploadedBy.isNotBlank() && photo.uploadedBy == currentUid
            val isAdmin = adminRepo.isAdminLoggedIn
            if (isOwner || isAdmin) {
                deleteBtn.visibility = View.VISIBLE
                deleteBtn.text = if (isAdmin && !isOwner) "Moderate / Delete Photo" else "Delete This Photo"
                deleteBtn.setOnClickListener {
                    deleteBtn.isEnabled = false
                    deleteBtn.text = "Deleting..."
                    if (isAdmin && !isOwner) {
                        adminRepo.deletePhotoAsAdmin(photo) { err ->
                            mainHandler.post {
                                if (err != null) {
                                    Toast.makeText(this, "Failed to delete: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    deleteBtn.isEnabled = true
                                    deleteBtn.text = "Moderate / Delete Photo"
                                } else {
                                    Toast.makeText(this, "Photo deleted as Admin", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            }
                        }
                    } else {
                        photos.deletePhoto(photo) { err ->
                            mainHandler.post {
                                if (err != null) {
                                    Toast.makeText(this, "Failed to delete: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    deleteBtn.isEnabled = true
                                    deleteBtn.text = "Delete This Photo"
                                } else {
                                    Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                            }
                        }
                    }
                }
            } else {
                deleteBtn.visibility = View.GONE
            }
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ────────────────────────────────────────────────────────
    //  Nearby Pandals Sheet (Part 5)
    // ────────────────────────────────────────────────────────

    private fun showNearbySheet() {
        val userLoc = location
        if (userLoc == null) {
            Toast.makeText(this, "Current GPS location required for Nearby mode", Toast.LENGTH_SHORT).show()
            requestLocationPermission(isExplicitUserAction = true)
            return
        }

        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_nearby_pandals, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeNearbySheet).setOnClickListener { sheet.dismiss() }

        val r1 = view.findViewById<MaterialButton>(R.id.radius1km)
        val r3 = view.findViewById<MaterialButton>(R.id.radius3km)
        val r5 = view.findViewById<MaterialButton>(R.id.radius5km)
        val countLabel = view.findViewById<TextView>(R.id.nearbyCountLabel)
        val rv = view.findViewById<RecyclerView>(R.id.nearbyRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)

        var selectedRadius = 3000f

        fun renderNearbyList() {
            val allNearby = pandals.nearby(userLoc)
            val filtered = allNearby.filter { it.distanceMeters <= selectedRadius }
            countLabel.text = "${filtered.size} pandals within ${"%.0f".format(selectedRadius / 1000)} km of you"
            rv.adapter = NearbyPandalAdapter(filtered) { p ->
                sheet.dismiss()
                focusPandal(p)
                showDetail(p)
            }
        }

        fun updateRadiusButtons(active: MaterialButton) {
            val primaryBg = ContextCompat.getColorStateList(this, R.color.primary)
            val surfaceBg = ContextCompat.getColorStateList(this, R.color.surface_floating)
            val strokePrimary = ContextCompat.getColorStateList(this, R.color.glass_stroke_primary)
            val strokeGlass = ContextCompat.getColorStateList(this, R.color.glass_stroke)
            listOf(r1, r3, r5).forEach { btn ->
                if (btn == active) {
                    btn.backgroundTintList = primaryBg
                    btn.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                    btn.strokeColor = strokePrimary
                } else {
                    btn.backgroundTintList = surfaceBg
                    btn.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                    btn.strokeColor = strokeGlass
                }
            }
            renderNearbyList()
        }

        r1.setOnClickListener { animateButtonPress(r1); selectedRadius = 1000f; updateRadiusButtons(r1) }
        r3.setOnClickListener { animateButtonPress(r3); selectedRadius = 3000f; updateRadiusButtons(r3) }
        r5.setOnClickListener { animateButtonPress(r5); selectedRadius = 5000f; updateRadiusButtons(r5) }

        renderNearbyList()
        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  My Pandal Sheet (Unified Personal Hub: Saved & Passport)
    // ────────────────────────────────────────────────────────

    private fun showMyPandalSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_my_pandal, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeMyPandalSheet).setOnClickListener { sheet.dismiss() }

        val savedCount = favorites.getFavoriteIds().size
        val savedCountView = view.findViewById<TextView>(R.id.myPandalSavedCount)
        savedCountView.text = if (savedCount == 1) "1 saved pandal" else "$savedCount saved pandals"

        val savedRatingTextView = view.findViewById<TextView>(R.id.myPandalSavedRatingText)
        val savedIds = favorites.getFavoriteIds()
        if (savedIds.isEmpty()) {
            savedRatingTextView?.text = "—"
        } else {
            savedRatingTextView?.text = "…"
            ratingRepo.fetchAggregateRatingForPandals(savedIds) { avg, _ ->
                mainHandler.post {
                    savedRatingTextView?.text = RatingRepository.formatRatingDisplay(avg)
                }
            }
        }

        val visitedCount = passport.getVisitedCount()
        val totalPandals = pandals.all().size
        val percent = if (totalPandals > 0) ((visitedCount.toFloat() / totalPandals) * 100).toInt() else 0
        val passportCountView = view.findViewById<TextView>(R.id.myPandalPassportCount)
        passportCountView.text = if (visitedCount == 1) "1 pandal visited ($percent%)" else "$visitedCount pandals visited ($percent%)"

        val passportProgressBar = view.findViewById<ProgressBar>(R.id.myPandalPassportProgressBar)
        passportProgressBar?.progress = percent

        val progressTextView = view.findViewById<TextView>(R.id.myPandalPassportProgressText)
        val areaMap = passport.getAreaBreakdown()
        if (areaMap.isNotEmpty()) {
            progressTextView.text = "${areaMap.size} areas explored • Tap to view passport"
        } else {
            progressTextView.text = "Track Puja progress & collected pandals"
        }

        val photosCountText = view.findViewById<TextView>(R.id.userPhotosCountText)
        val crowdCountText = view.findViewById<TextView>(R.id.userCrowdCountText)
        val weatherCountText = view.findViewById<TextView>(R.id.userWeatherCountText)
        val contributionsSubtitle = view.findViewById<TextView>(R.id.myContributionsSubtitle)

        authRepo.getOrCreateUid { uid ->
            contributions.fetchUserContributions(uid) { userContribs ->
                mainHandler.post {
                    photosCountText.text = userContribs.photosCount.toString()
                    crowdCountText.text = userContribs.crowdReportsCount.toString()
                    weatherCountText?.text = userContribs.weatherReportsCount.toString()
                    val total = userContribs.total
                    contributionsSubtitle.text = if (total == 0) "No community contributions yet" else "$total total community contributions"
                }
            }
        }

        view.findViewById<View>(R.id.btnMyPhotosManage).setOnClickListener {
            sheet.dismiss()
            showMyPhotosSheet()
        }

        view.findViewById<View>(R.id.myPandalSavedCard).setOnClickListener {
            sheet.dismiss()
            showSavedFavoritesSheet()
        }

        view.findViewById<View>(R.id.myPandalPassportCard).setOnClickListener {
            sheet.dismiss()
            showPassportSheet()
        }

        // Hidden 7-tap admin portal trigger on the version label
        val versionLabel = view.findViewById<TextView>(R.id.appVersionLabel)
        var versionTapCount = 0
        var lastTapTime = 0L

        versionLabel.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime > 3000L) {
                versionTapCount = 1
            } else {
                versionTapCount++
            }
            lastTapTime = now

            if (versionTapCount >= 7) {
                versionTapCount = 0
                sheet.dismiss()
                if (adminRepo.isAdminLoggedIn) {
                    showAdminPanelSheet()
                } else {
                    showAdminLoginDialog()
                }
            }
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Admin Authentication & Admin Dashboard
    // ────────────────────────────────────────────────────────

    private fun showAdminLoginDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_admin_login, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val emailInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.adminEmailInput)
        val passwordInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.adminPasswordInput)
        val errorText = view.findViewById<TextView>(R.id.adminLoginErrorText)
        val progressBar = view.findViewById<ProgressBar>(R.id.adminLoginProgress)
        val signInBtn = view.findViewById<MaterialButton>(R.id.adminSignInBtn)
        val closeBtn = view.findViewById<View>(R.id.adminLoginCloseBtn)

        closeBtn.setOnClickListener { dialog.dismiss() }

        signInBtn.setOnClickListener {
            val email = emailInput.text?.toString().orEmpty().trim()
            val password = passwordInput.text?.toString().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                errorText.text = "Please enter both admin email and password."
                errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            errorText.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            signInBtn.isEnabled = false
            signInBtn.text = "Verifying..."

            adminRepo.signInAdmin(email, password, authRepo) { result ->
                mainHandler.post {
                    progressBar.visibility = View.GONE
                    signInBtn.isEnabled = true
                    signInBtn.text = "Sign In"

                    result.onSuccess {
                        dialog.dismiss()
                        Toast.makeText(this, "Admin session verified", Toast.LENGTH_SHORT).show()
                        showAdminPanelSheet()
                    }.onFailure {
                        // Generic error message without revealing account existence
                        errorText.text = "Admin access denied."
                        errorText.visibility = View.VISIBLE
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showAdminPanelSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_admin_panel, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeAdminPanelBtn).setOnClickListener { sheet.dismiss() }

        val subtitle = view.findViewById<TextView>(R.id.adminAccountSubtitle)
        val adminEmail = adminRepo.currentAdminEmail ?: "Authorized Administrator"
        subtitle.text = "Signed in as $adminEmail"

        val tabPhotos = view.findViewById<MaterialCardView>(R.id.adminTabPhotos)
        val tabPhotosText = view.findViewById<TextView>(R.id.adminTabPhotosText)
        val tabCrowd = view.findViewById<MaterialCardView>(R.id.adminTabCrowd)
        val tabCrowdText = view.findViewById<TextView>(R.id.adminTabCrowdText)
        val tabWeather = view.findViewById<MaterialCardView>(R.id.adminTabWeather)
        val tabWeatherText = view.findViewById<TextView>(R.id.adminTabWeatherText)

        val loadingProgress = view.findViewById<ProgressBar>(R.id.adminLoadingProgress)
        val sectionSubtitle = view.findViewById<TextView>(R.id.adminSectionSubtitle)
        val emptyText = view.findViewById<TextView>(R.id.adminEmptyText)
        val recyclerView = view.findViewById<RecyclerView>(R.id.adminContentRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val pandalMap = pandals.all().associateBy { it.id }

        var currentTab = "photos" // "photos", "crowd", "weather"

        fun updateTabStyles() {
            if (currentTab == "photos") {
                tabPhotos.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
                tabPhotos.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
                tabPhotosText.setTextColor(ContextCompat.getColor(this, R.color.primary))
                tabPhotosText.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tabPhotos.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                tabPhotos.strokeColor = ContextCompat.getColor(this, R.color.outline)
                tabPhotosText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tabPhotosText.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            if (currentTab == "crowd") {
                tabCrowd.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
                tabCrowd.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
                tabCrowdText.setTextColor(ContextCompat.getColor(this, R.color.primary))
                tabCrowdText.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tabCrowd.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                tabCrowd.strokeColor = ContextCompat.getColor(this, R.color.outline)
                tabCrowdText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tabCrowdText.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            if (currentTab == "weather") {
                tabWeather.setCardBackgroundColor(ContextCompat.getColor(this, R.color.weather_surface))
                tabWeather.strokeColor = ContextCompat.getColor(this, R.color.weather_outline)
                tabWeatherText.setTextColor(ContextCompat.getColor(this, R.color.weather_blue))
                tabWeatherText.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                tabWeather.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                tabWeather.strokeColor = ContextCompat.getColor(this, R.color.outline)
                tabWeatherText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                tabWeatherText.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }

        fun loadPhotos() {
            currentTab = "photos"
            updateTabStyles()
            loadingProgress.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.GONE
            sectionSubtitle.text = "Loading community photos..."

            adminRepo.fetchAdminPhotos { photosList ->
                mainHandler.post {
                    loadingProgress.visibility = View.GONE
                    if (photosList.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "No community photos found."
                        recyclerView.visibility = View.GONE
                        sectionSubtitle.text = "0 photos"
                    } else {
                        emptyText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        sectionSubtitle.text = "${photosList.size} community photos across all pandals"

                        val adapter = com.pandalfinder.ui.AdminPhotosAdapter(
                            photos = photosList,
                            pandalMap = pandalMap,
                            onPhotoClick = { photo ->
                                showFullPhotoDialog(photo, photosList, pandalMap[photo.pandalId]?.name ?: "Admin Moderation")
                            },
                            onDeleteClick = { photo ->
                                androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Admin Moderation")
                                    .setMessage("Delete photo permanently from Storage and Firestore?")
                                    .setPositiveButton("Delete") { _, _ ->
                                        Toast.makeText(this, "Deleting photo...", Toast.LENGTH_SHORT).show()
                                        adminRepo.deletePhotoAsAdmin(photo) { err ->
                                            mainHandler.post {
                                                if (err != null) {
                                                    Toast.makeText(this, "Failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this, "Photo deleted by Admin", Toast.LENGTH_SHORT).show()
                                                    loadPhotos()
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        )
                        recyclerView.adapter = adapter
                    }
                }
            }
        }

        fun loadCrowd() {
            currentTab = "crowd"
            updateTabStyles()
            loadingProgress.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.GONE
            sectionSubtitle.text = "Loading crowd reports..."

            adminRepo.fetchRecentCrowdReports { reportsList ->
                mainHandler.post {
                    loadingProgress.visibility = View.GONE
                    if (reportsList.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "No crowd reports found."
                        recyclerView.visibility = View.GONE
                        sectionSubtitle.text = "0 reports"
                    } else {
                        emptyText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        sectionSubtitle.text = "${reportsList.size} crowd reports"

                        val adapter = com.pandalfinder.ui.AdminCrowdAdapter(
                            reports = reportsList,
                            pandalMap = pandalMap,
                            onDeleteClick = { report ->
                                androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Admin Delete Report")
                                    .setMessage("Delete this crowd report?")
                                    .setPositiveButton("Delete") { _, _ ->
                                        adminRepo.deleteCrowdReport(report.id) { err ->
                                            mainHandler.post {
                                                if (err != null) {
                                                    Toast.makeText(this, "Failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this, "Report deleted", Toast.LENGTH_SHORT).show()
                                                    loadCrowd()
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        )
                        recyclerView.adapter = adapter
                    }
                }
            }
        }

        fun loadWeather() {
            currentTab = "weather"
            updateTabStyles()
            loadingProgress.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.GONE
            sectionSubtitle.text = "Loading weather reports..."

            adminRepo.fetchRecentWeatherReports { reportsList ->
                mainHandler.post {
                    loadingProgress.visibility = View.GONE
                    if (reportsList.isEmpty()) {
                        emptyText.visibility = View.VISIBLE
                        emptyText.text = "No weather reports found."
                        recyclerView.visibility = View.GONE
                        sectionSubtitle.text = "0 reports"
                    } else {
                        emptyText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        sectionSubtitle.text = "${reportsList.size} weather reports"

                        val adapter = com.pandalfinder.ui.AdminWeatherAdapter(
                            reports = reportsList,
                            pandalMap = pandalMap,
                            onDeleteClick = { report ->
                                androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Admin Delete Weather Report")
                                    .setMessage("Delete this weather report?")
                                    .setPositiveButton("Delete") { _, _ ->
                                        adminRepo.deleteWeatherReport(report.id) { err ->
                                            mainHandler.post {
                                                if (err != null) {
                                                    Toast.makeText(this, "Failed: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this, "Weather report deleted", Toast.LENGTH_SHORT).show()
                                                    loadWeather()
                                                }
                                            }
                                        }
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                        )
                        recyclerView.adapter = adapter
                    }
                }
            }
        }

        tabPhotos.setOnClickListener { loadPhotos() }
        tabCrowd.setOnClickListener { loadCrowd() }
        tabWeather.setOnClickListener { loadWeather() }

        view.findViewById<View>(R.id.adminSignOutBtn).setOnClickListener {
            adminRepo.signOutAdmin(authRepo) {
                mainHandler.post {
                    Toast.makeText(this, "Signed out of admin session", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                }
            }
        }

        loadPhotos()
        sheet.show()
    }

    private fun showMyPhotosSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_my_photos, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeMyPhotosSheet).setOnClickListener { sheet.dismiss() }

        val loadingProgress = view.findViewById<ProgressBar>(R.id.myPhotosLoadingProgress)
        val emptyLayout = view.findViewById<View>(R.id.myPhotosEmptyLayout)
        val subtitle = view.findViewById<TextView>(R.id.myPhotosCountSubtitle)
        val recyclerView = view.findViewById<RecyclerView>(R.id.myPhotosRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val pandalMap = pandals.all().associateBy { it.id }

        fun refreshUserPhotos() {
            loadingProgress.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
            recyclerView.visibility = View.GONE

            authRepo.getOrCreateUid { uid ->
                contributions.fetchUserPhotos(uid) { userPhotoList ->
                    mainHandler.post {
                        loadingProgress.visibility = View.GONE
                        if (userPhotoList.isEmpty()) {
                            emptyLayout.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                            subtitle.text = "No photos uploaded yet"
                        } else {
                            emptyLayout.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            subtitle.text = if (userPhotoList.size == 1) "1 photo uploaded" else "${userPhotoList.size} photos uploaded"

                            val adapter = com.pandalfinder.ui.MyPhotosAdapter(
                                photos = userPhotoList,
                                pandalMap = pandalMap,
                                onPhotoClick = { photo ->
                                    val pandal = pandalMap[photo.pandalId] ?: pandalMap["pandal:${photo.pandalId}"]
                                    showFullscreenPhotoViewer(userPhotoList, userPhotoList.indexOf(photo).coerceAtLeast(0), pandal?.name ?: "My Photo")
                                },
                                onReplace = { photo ->
                                    val pandal = pandalMap[photo.pandalId] ?: pandalMap["pandal:${photo.pandalId}"]
                                    val pandalName = pandal?.name ?: "Pandal"
                                    showReplacePhotoSourceDialog(photo, pandalName) {
                                        refreshUserPhotos()
                                    }
                                },
                                onDelete = { photo ->
                                    val pandal = pandalMap[photo.pandalId] ?: pandalMap["pandal:${photo.pandalId}"]
                                    val pandalName = pandal?.name ?: "Pandal"
                                    androidx.appcompat.app.AlertDialog.Builder(this)
                                        .setTitle("Delete Photo")
                                        .setMessage("Are you sure you want to delete your photo for $pandalName? This cannot be undone.")
                                        .setPositiveButton("Delete") { _, _ ->
                                            Toast.makeText(this, "Deleting photo...", Toast.LENGTH_SHORT).show()
                                            photos.deletePhoto(photo) { err ->
                                                mainHandler.post {
                                                    if (err != null) {
                                                        val msg = err.localizedMessage ?: "Deletion failed"
                                                        Toast.makeText(this, "Failed to delete: $msg", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(this, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
                                                        refreshUserPhotos()
                                                    }
                                                }
                                            }
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                }
                            )
                            recyclerView.adapter = adapter
                        }
                    }
                }
            }
        }

        refreshUserPhotos()
        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Saved / Favorites Sheet (Part 6)
    // ────────────────────────────────────────────────────────

    private fun showSavedFavoritesSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_saved_favorites, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeSavedSheet).setOnClickListener { sheet.dismiss() }

        val countSubtitle = view.findViewById<TextView>(R.id.savedCountSubtitle)
        val emptyText = view.findViewById<TextView>(R.id.savedEmptyText)
        val rv = view.findViewById<RecyclerView>(R.id.savedRecyclerView)
        val btnAddAll = view.findViewById<MaterialButton>(R.id.btnAddAllSavedToHopping)
        rv.layoutManager = LinearLayoutManager(this)

        fun refreshSaved() {
            val savedList = favorites.getFavoritePandals(pandals)
            countSubtitle.text = "${savedList.size} bookmarked pandals"
            if (savedList.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                rv.visibility = View.GONE
                btnAddAll.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                rv.visibility = View.VISIBLE
                btnAddAll.visibility = View.VISIBLE
                rv.adapter = SavedPandalAdapter(
                    savedList,
                    onSelect = { p ->
                        sheet.dismiss()
                        focusPandal(p)
                        showDetail(p)
                    },
                    onRemove = { p ->
                        favorites.toggleFavorite(p.id)
                        updateFilterPillStyles()
                        refreshSaved()
                    }
                )
            }
        }

        btnAddAll.setOnClickListener {
            val savedList = favorites.getFavoritePandals(pandals)
            var addedCount = 0
            savedList.forEach { p ->
                if (hopping.addPandal(p)) addedCount++
            }
            sheet.dismiss()
            selectTab(isMap = false)
            Toast.makeText(this, "Added $addedCount pandals to Hopping Trail", Toast.LENGTH_SHORT).show()
        }

        refreshSaved()
        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Pandal Passport Sheet (Part 7)
    // ────────────────────────────────────────────────────────

    private fun showPassportSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_pandal_passport, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closePassportSheet).setOnClickListener { sheet.dismiss() }

        val visitedCountText = view.findViewById<TextView>(R.id.passportVisitedCountText)
        val percentText = view.findViewById<TextView>(R.id.passportPercentText)
        val progressBar = view.findViewById<ProgressBar>(R.id.passportProgressBar)
        val areasExplored = view.findViewById<TextView>(R.id.passportAreasExploredText)
        val rv = view.findViewById<RecyclerView>(R.id.passportRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)

        val total = pandals.all().size
        val visited = passport.getVisitedCount()
        val percent = if (total > 0) ((visited.toFloat() / total) * 100).toInt() else 0

        visitedCountText.text = "$visited / $total visited"
        percentText.text = "$percent%"
        progressBar.progress = percent

        val areaMap = passport.getAreaBreakdown()
        if (areaMap.isNotEmpty()) {
            areasExplored.text = areaMap.entries.joinToString("  •  ") { "${it.key}: ${it.value}" }
        } else {
            areasExplored.text = "Explore pandals around Kolkata to stamp your passport."
        }

        val visitedRecords = passport.getVisitedRecords()
        rv.adapter = PassportRecordAdapter(visitedRecords) { record ->
            val p = pandals.all().firstOrNull { it.id == record.pandalId }
            if (p != null) {
                sheet.dismiss()
                focusPandal(p)
                showDetail(p)
            }
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Explore Zones Sheet (Part 10)
    // ────────────────────────────────────────────────────────

    private fun showExploreZonesSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_explore_zones, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeZonesSheet).setOnClickListener { sheet.dismiss() }

        val rv = view.findViewById<RecyclerView>(R.id.zonesRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)

        val zoneList = zones.getZones()
        rv.adapter = FestivalZoneAdapter(
            zoneList,
            onExplore = { zone ->
                sheet.dismiss()
                filterShowPandals = true
                updateFilterPillStyles()
                applyCurrentFilters()
                map?.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(zone.centerLat, zone.centerLng))
                    .zoom(13.5)
                    .build()
                Toast.makeText(this, "Exploring ${zone.name}", Toast.LENGTH_SHORT).show()
            },
            onAddToHopping = { zone ->
                val topPandals = zone.pandals.take(4)
                var added = 0
                topPandals.forEach { if (hopping.addPandal(it)) added++ }
                sheet.dismiss()
                selectTab(isMap = false)
                Toast.makeText(this, "Added $added pandals from ${zone.name} to Trail", Toast.LENGTH_SHORT).show()
            }
        )

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Smart Route Optimizer Dialog (Part 3)
    // ────────────────────────────────────────────────────────

    private fun showOptimizeRouteDialog() {
        val plan = hopping.getPlan()
        if (plan.size < 2) {
            Toast.makeText(this, "Add at least 2 stops to optimize route", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_optimize_route, null)
        dialog.setContentView(view)

        view.findViewById<MaterialButton>(R.id.btnCancelOptimize).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnConfirmOptimize).setOnClickListener {
            dialog.dismiss()
            val result = RouteOptimizer.optimize(location, plan)
            if (result.wasReordered) {
                hopping.clear()
                result.optimizedStops.forEach { s ->
                    when (s.type) {
                        StopType.PANDAL -> s.pandalRef?.let { hopping.addPandal(it) }
                        StopType.METRO -> s.metroRef?.let { hopping.addMetro(it) }
                        StopType.TOILET -> s.toiletRef?.let { hopping.addToilet(it) }
                    }
                }
                val savedKm = "%.1f".format(result.savedDistanceMeters / 1000.0)
                Toast.makeText(this, "Route optimized! Reduced travel by ~$savedKm km", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Your route is already optimal!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // ────────────────────────────────────────────────────────
    //  Tonight's Plan Generator Sheet (Part 11)
    // ────────────────────────────────────────────────────────

    private fun showTonightsPlanSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_tonights_plan, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closePlanSheet).setOnClickListener { sheet.dismiss() }

        val planTitle = view.findViewById<TextView>(R.id.planTitle)
        val planDesc = view.findViewById<TextView>(R.id.planDescription)
        val statsSummary = view.findViewById<TextView>(R.id.planStatsSummary)
        val rv = view.findViewById<RecyclerView>(R.id.planStopsRecyclerView)
        val btnAddPlan = view.findViewById<MaterialButton>(R.id.btnAddPlanToHopping)
        rv.layoutManager = LinearLayoutManager(this)

        val tonightsPlan = TonightsPlanGenerator.generate(
            location,
            pandals,
            metro,
            places.cachedToilets,
            liveCrowdMap
        )

        planTitle.text = tonightsPlan.title
        planDesc.text = tonightsPlan.description

        val distKm = "%.1f km".format(tonightsPlan.totalDistanceMeters / 1000.0)
        val hours = tonightsPlan.estimatedDurationMinutes / 60
        val mins = tonightsPlan.estimatedDurationMinutes % 60
        val timeStr = if (hours > 0) "~${hours}h ${mins}m" else "~${mins}m"
        val countLabel = if (tonightsPlan.pandalCount > 0) "${tonightsPlan.pandalCount} pandals" else "${tonightsPlan.stops.size} stops"
        statsSummary.text = "$countLabel • $distKm • $timeStr"

        rv.adapter = PlanStopsPreviewAdapter(tonightsPlan.stops, tonightsPlan.legs)

        btnAddPlan.setOnClickListener {
            sheet.dismiss()
            tonightsPlan.stops.forEach { s ->
                when (s.type) {
                    StopType.PANDAL -> s.pandalRef?.let { hopping.addPandal(it) }
                    StopType.METRO -> s.metroRef?.let { hopping.addMetro(it) }
                    StopType.TOILET -> s.toiletRef?.let { hopping.addToilet(it) }
                }
            }
            selectTab(isMap = false)
            Toast.makeText(this, "Tonight's Plan added to your Hopping Trail!", Toast.LENGTH_SHORT).show()
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Weather contribution modal bottom sheet (Rain status)
    // ────────────────────────────────────────────────────────

    private fun showRainStatusDialog(item: Pandal, onRefreshed: (() -> Unit)? = null) {
        val currentLoc = location
        if (currentLoc == null) {
            Toast.makeText(this, "Location required to update weather. Please enable Location.", Toast.LENGTH_SHORT).show()
            checkLocationSettingsAndStart(explicitUserAction = true)
            return
        }

        val dist = RouteOptimizer.distanceBetween(
            currentLoc.latitude,
            currentLoc.longitude,
            item.latitude,
            item.longitude
        )

        if (dist > WeatherReportRepository.MAX_DISTANCE_METERS) {
            val distStr = if (dist >= 1000f) "%.1f km".format(dist / 1000f) else "${dist.toInt()} m"
            Toast.makeText(
                this,
                "Move within 500 m of this Pandal to update the current weather. (Currently $distStr away)",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_rain_status, null)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.tvRainPopupSubtitle).text = "Report current weather at ${item.name}"
        view.findViewById<View>(R.id.btnRainPopupClose).setOnClickListener { sheet.dismiss() }

        val cardNoRain = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardNoRain)
        val cardDrizzle = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardDrizzle)
        val cardRaining = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardRaining)
        val cardHeavyRain = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardHeavyRain)

        val options = listOf(
            cardNoRain to CommunityWeather.NO_RAIN,
            cardDrizzle to CommunityWeather.DRIZZLE,
            cardRaining to CommunityWeather.RAINING,
            cardHeavyRain to CommunityWeather.HEAVY_RAIN
        )

        fun selectAndSubmit(selectedCard: com.google.android.material.card.MaterialCardView, condition: CommunityWeather) {
            options.forEach { (card, _) ->
                if (card == selectedCard) {
                    card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
                    card.strokeColor = ContextCompat.getColor(this, R.color.primary)
                } else {
                    card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                    card.strokeColor = ContextCompat.getColor(this, R.color.weather_outline)
                }
            }

            weatherReports.submitWeatherObservation(item, condition, currentLoc) { success, err ->
                if (success) {
                    Toast.makeText(this, "Weather updated", Toast.LENGTH_SHORT).show()
                    onRefreshed?.invoke()
                    sheet.dismiss()
                } else {
                    Toast.makeText(this, err ?: "Couldn't update weather. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        cardNoRain.setOnClickListener { selectAndSubmit(cardNoRain, CommunityWeather.NO_RAIN) }
        cardDrizzle.setOnClickListener { selectAndSubmit(cardDrizzle, CommunityWeather.DRIZZLE) }
        cardRaining.setOnClickListener { selectAndSubmit(cardRaining, CommunityWeather.RAINING) }
        cardHeavyRain.setOnClickListener { selectAndSubmit(cardHeavyRain, CommunityWeather.HEAVY_RAIN) }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Crowd contribution bottom sheet
    // ────────────────────────────────────────────────────────

    private fun crowdDialog(item: Pandal, onRefreshed: () -> Unit) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_crowd_report, null)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.crowdPandalName).text = item.name
        view.findViewById<View>(R.id.closeCrowdSheet).setOnClickListener { sheet.dismiss() }

        val banner = view.findViewById<View>(R.id.crowdEligibilityBanner)
        val bannerText = view.findViewById<TextView>(R.id.crowdEligibilityText)
        val grid = view.findViewById<GridLayout>(R.id.crowdGrid)
        val submit = view.findViewById<MaterialButton>(R.id.submitCrowd)
        val progress = view.findViewById<ProgressBar>(R.id.crowdProgress)
        val feedback = view.findViewById<LinearLayout>(R.id.crowdFeedback)
        val levelDisplay = view.findViewById<TextView>(R.id.crowdLevelDisplay)
        val labelText = view.findViewById<TextView>(R.id.crowdLabelText)
        val descText = view.findViewById<TextView>(R.id.crowdDescriptionText)

        val eligibilityResult = eligibility.evaluate(item, location)
        if (!eligibilityResult.allowed) {
            banner.visibility = View.VISIBLE
            bannerText.text = eligibilityResult.reason
        } else {
            banner.visibility = View.GONE
        }

        var selection: Int? = null

        (1..10).forEach { level ->
            val button = MaterialButton(this).apply {
                text = level.toString()
                isAllCaps = false
                textSize = 18f
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(56)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setOnClickListener {
                    selection = level
                    feedback.visibility = View.VISIBLE

                    for (i in 0 until grid.childCount) {
                        val child = grid.getChildAt(i) as MaterialButton
                        child.isChecked = (i + 1 == level)
                    }

                    levelDisplay.text = "$level / 10"
                    labelText.text = crowdLabelText(level)
                    descText.text = crowdDescription(level)

                    if (eligibilityResult.allowed) {
                        submit.isEnabled = true
                    }
                }
            }
            grid.addView(button)
        }

        submit.setOnClickListener {
            val chosen = selection ?: return@setOnClickListener
            submit.isEnabled = false
            progress.visibility = View.VISIBLE

            val devId = eligibilityResult.deviceId
            authRepo.getOrCreateUid { uid ->
                crowd.submit(item, chosen, uid, devId, location) { error ->
                    mainHandler.post {
                        progress.visibility = View.GONE
                        if (error == null) {
                            Toast.makeText(this, "Crowd update submitted. Thank you for contributing!", Toast.LENGTH_SHORT).show()
                            onRefreshed()
                            loadLiveCrowdHeatmap()
                            sheet.dismiss()
                        } else {
                            Log.e(TAG, "Failed to submit crowd report: ${error.message}", error)
                            Toast.makeText(this, "Failed to submit: ${error.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                            submit.isEnabled = true
                        }
                    }
                }
            }
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun ago(time: Long): String {
        val minutes = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)
        return if (minutes < 1) "just now" else "$minutes min ago"
    }

    companion object {
        private const val TAG = "PandalFinderMain"
        const val LOCATION_TAG = "PandalQuest-Location"
        const val KEY_HAS_REQUESTED_LOCATION_PERMISSION = "has_requested_location_permission"
        const val REQUEST_LOCATION = 1001
        const val REQUEST_CHECK_SETTINGS = 1002

        fun checkPermanentDenial(
            hasPermission: Boolean,
            hasRequestedBefore: Boolean,
            shouldShowRationale: Boolean
        ): Boolean {
            if (hasPermission) return false
            if (!hasRequestedBefore) return false
            return !shouldShowRationale
        }

        fun isValidCoordinate(lat: Double, lng: Double): Boolean =
            !lat.isNaN() && !lat.isInfinite() && !lng.isNaN() && !lng.isInfinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0

        fun distanceText(meters: Float): String = when {
            meters < 50 -> "< 50 m away"
            meters < 1000 -> "${(meters / 50).toInt() * 50} m away"
            else -> "%.1f km away".format(meters / 1000)
        }

        fun distanceShort(meters: Float): String = when {
            meters < 50 -> "< 50 m"
            meters < 1000 -> "${(meters / 50).toInt() * 50} m"
            else -> "%.1f km".format(meters / 1000)
        }

        fun routeDistanceText(meters: Int): String = when {
            meters < 1_000 -> "${(meters / 50) * 50} m"
            else -> "%.1f km".format(meters / 1_000.0)
        }

        fun crowdLabelText(level: Int): String = when (level) {
            1, 2 -> "Empty"
            3, 4 -> "Light"
            5, 6 -> "Moderate"
            7, 8 -> "Very busy"
            9 -> "Extremely busy"
            10 -> "Packed"
            else -> ""
        }

        fun crowdDescription(level: Int): String = when (level) {
            1 -> "Little to no crowd."
            2 -> "Almost empty, very easy to visit."
            3 -> "Light crowd, comfortable."
            4 -> "Some people around, easy movement."
            5 -> "Comfortable but noticeable crowd."
            6 -> "Moderate crowd, some waiting."
            7 -> "Busy, expect some queues."
            8 -> "Very busy, slow movement in places."
            9 -> "Extremely busy, long queues and slow movement."
            10 -> "Very dense crowd and slow movement."
            else -> ""
        }
    }
}

// ────────────────────────────────────────────────────────────
//  Adapters
// ────────────────────────────────────────────────────────────

private class SearchResultAdapter(
    private val items: List<SearchResult>,
    private val selected: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val iconContainer: FrameLayout = v.findViewById(R.id.resultIconContainer)
        val icon: ImageView = v.findViewById(R.id.resultIcon)
        val name: TextView = v.findViewById(R.id.resultName)
        val area: TextView = v.findViewById(R.id.resultArea)
        val distance: TextView = v.findViewById(R.id.resultDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        when (item.type) {
            PlaceType.PANDAL -> {
                holder.icon.setImageResource(R.drawable.ic_pandal_icon)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.primary))
                holder.iconContainer.setBackgroundResource(R.drawable.bg_lens_red)
                holder.iconContainer.backgroundTintList = null
                holder.name.text = item.name
                holder.area.text = item.subtitle
                holder.distance.text = if (item.distanceMeters > 0) {
                    MainActivity.distanceShort(item.distanceMeters)
                } else ""
            }
            PlaceType.METRO -> {
                holder.icon.setImageResource(R.drawable.ic_metro_train)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.metro_icon))
                holder.iconContainer.setBackgroundResource(R.drawable.bg_lens_metro)
                holder.iconContainer.backgroundTintList = null
                holder.name.text = item.name
                holder.area.text = item.subtitle
                holder.distance.text = if (item.distanceMeters > 0) {
                    MainActivity.distanceShort(item.distanceMeters)
                } else ""
            }
            PlaceType.TOILET -> {
                holder.icon.setImageResource(R.drawable.ic_restroom)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.toilet_icon))
                holder.iconContainer.setBackgroundResource(R.drawable.bg_lens_toilet)
                holder.iconContainer.backgroundTintList = null
                holder.name.text = item.name
                holder.area.text = item.subtitle
                holder.distance.text = if (item.distanceMeters > 0) {
                    MainActivity.distanceShort(item.distanceMeters)
                } else ""
            }
        }
        holder.itemView.setOnClickListener { selected(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class NearbyPandalAdapter(
    private val items: List<Pandal>,
    private val onSelect: (Pandal) -> Unit
) : RecyclerView.Adapter<NearbyPandalAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.nearbyName)
        val subtitle: TextView = v.findViewById(R.id.nearbySubtitle)
        val distance: TextView = v.findViewById(R.id.nearbyDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_nearby_pandal, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.subtitle.text = item.area
        holder.distance.text = MainActivity.distanceShort(item.distanceMeters)
        holder.itemView.setOnClickListener { onSelect(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class SavedPandalAdapter(
    private val items: List<Pandal>,
    private val onSelect: (Pandal) -> Unit,
    private val onRemove: (Pandal) -> Unit
) : RecyclerView.Adapter<SavedPandalAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.savedPandalName)
        val area: TextView = v.findViewById(R.id.savedPandalArea)
        val btnRemove: ImageButton = v.findViewById(R.id.btnRemoveSaved)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_saved_pandal, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.area.text = item.area
        holder.itemView.setOnClickListener { onSelect(item) }
        holder.btnRemove.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class PassportRecordAdapter(
    private val items: List<VisitedPandalRecord>,
    private val onSelect: (VisitedPandalRecord) -> Unit
) : RecyclerView.Adapter<PassportRecordAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.passportPandalName)
        val areaAndTime: TextView = v.findViewById(R.id.passportPandalAreaAndTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_passport_visited, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.pandalName
        val timeStr = if (item.visitedAtTimestamp > 0) {
            val min = ((System.currentTimeMillis() - item.visitedAtTimestamp) / 60_000).coerceAtLeast(0)
            if (min < 60) "$min min ago" else "${min / 60}h ago"
        } else "Visited"
        holder.areaAndTime.text = "${item.area} • $timeStr"
        holder.itemView.setOnClickListener { onSelect(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class FestivalZoneAdapter(
    private val items: List<FestivalZone>,
    private val onExplore: (FestivalZone) -> Unit,
    private val onAddToHopping: (FestivalZone) -> Unit
) : RecyclerView.Adapter<FestivalZoneAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.zoneName)
        val countBadge: TextView = v.findViewById(R.id.zoneCountBadge)
        val description: TextView = v.findViewById(R.id.zoneDescription)
        val btnExplore: MaterialButton = v.findViewById(R.id.btnExploreZone)
        val btnAdd: MaterialButton = v.findViewById(R.id.btnAddZoneToHopping)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_festival_zone, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.countBadge.text = "${item.count} Pandals"
        holder.description.text = item.description
        holder.btnExplore.setOnClickListener { onExplore(item) }
        holder.btnAdd.setOnClickListener { onAddToHopping(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class PlanStopsPreviewAdapter(
    private val items: List<HoppingStop>,
    private val legs: List<PlanLeg> = emptyList()
) : RecyclerView.Adapter<PlanStopsPreviewAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val index: TextView = v.findViewById(R.id.planStopIndex)
        val icon: ImageView = v.findViewById(R.id.planStopIcon)
        val name: TextView = v.findViewById(R.id.planStopName)
        val subtitle: TextView = v.findViewById(R.id.planStopSubtitle)
        val legConnector: View? = v.findViewById(R.id.planLegConnector)
        val legIcon: ImageView? = v.findViewById(R.id.planLegIcon)
        val legText: TextView? = v.findViewById(R.id.planLegText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_plan_stop, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.index.text = (position + 1).toString()
        holder.name.text = item.name
        holder.subtitle.text = item.subtitle

        when (item.type) {
            StopType.PANDAL -> {
                holder.icon.setImageResource(R.drawable.ic_pandal_icon)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.primary))
            }
            StopType.METRO -> {
                holder.icon.setImageResource(R.drawable.ic_metro_train)
                val color = item.metroRef?.lineColor ?: ContextCompat.getColor(context, R.color.metro_icon)
                holder.icon.setColorFilter(color)
            }
            StopType.TOILET -> {
                holder.icon.setImageResource(R.drawable.ic_restroom)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.toilet_icon))
            }
        }

        // Display transit connector towards the next stop (if applicable)
        if (position < items.size - 1 && position + 1 < legs.size) {
            val leg = legs[position + 1]
            if (leg.isMetroBeneficial) {
                holder.legConnector?.visibility = View.VISIBLE
                holder.legIcon?.setImageResource(R.drawable.ic_metro_train)
                holder.legIcon?.setColorFilter(ContextCompat.getColor(context, R.color.secondary))
                holder.legText?.text = leg.transferDescription ?: "Metro transfer"
                holder.legText?.setTextColor(ContextCompat.getColor(context, R.color.secondary))
            } else if (!leg.transferDescription.isNullOrBlank()) {
                holder.legConnector?.visibility = View.VISIBLE
                holder.legIcon?.setImageResource(R.drawable.ic_navigation)
                holder.legIcon?.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary))
                holder.legText?.text = leg.transferDescription
                holder.legText?.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            } else {
                holder.legConnector?.visibility = View.GONE
            }
        } else {
            holder.legConnector?.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}
