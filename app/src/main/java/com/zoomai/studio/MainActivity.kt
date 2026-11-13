package com.zoomai.studio

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zoom AI Studio — v0.3 design: a calm, Samsung-style camera screen
 * (full-screen preview, quiet line icons, mode words, one big shutter),
 * a photo-based pose library, a list-style settings page and a separate
 * About page that holds all developer info.
 */
class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(5, 7, 11)
    private val panel = Color.rgb(13, 17, 24)
    private val card = Color.rgb(22, 28, 38)
    private val accent = Color.rgb(94, 169, 255)
    private val camAccent = Color.rgb(255, 204, 0)
    private val danger = Color.rgb(255, 82, 82)
    private val muted = Color.rgb(154, 167, 184)
    private val faint = Color.rgb(106, 118, 135)

    private fun s(fa: String, en: String) = I18n.s(fa, en)

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private var currentTab = 0
    private var inAbout = false
    private val navIcons = mutableListOf<ImageView>()
    private val navLabels = mutableListOf<TextView>()

    // Camera state
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var capture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recorder: Recorder? = null
    private var activeRecording: Recording? = null
    private var isRecording = false
    private var videoMode = false
    private var proMode = false
    private var backCamera = true
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var torchOn = false
    private var showGrid = true
    private var livePoseOn = false
    private var levelOn = false
    private var timerSec = 0
    private var countingDown = false
    private var aspectRatio = AspectRatio.RATIO_4_3
    private var poseOverlay: PoseOverlayView? = null
    private var ghostView: ImageView? = null
    private var ghostPoseFile: String? = null
    private var ghostPoseTitle: String? = null
    private var statusLabel: TextView? = null
    private var minZoom = 1f
    private var maxZoom = 1f
    private var recordStartedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Camera screen widget refs (for state refresh)
    private inner class IconBtn(val box: FrameLayout, val img: ImageView, val badge: TextView)
    private var btnFlash: IconBtn? = null
    private var btnTimer: IconBtn? = null
    private var btnGrid: IconBtn? = null
    private var btnTorch: IconBtn? = null
    private var btnPose: IconBtn? = null
    private var btnLive: IconBtn? = null
    private var btnLevel: IconBtn? = null
    private var ratioLabel: TextView? = null
    private var modeLabels = mutableListOf<TextView>()
    private var zoomChipRow: LinearLayout? = null
    private var zoomChips = mutableListOf<Pair<Float, TextView>>()
    private var proPanel: LinearLayout? = null
    private var evSeekBar: SeekBar? = null
    private var zoomSeek: SeekBar? = null
    private var poseStrip: View? = null
    private var shutterInner: View? = null

    // ML Kit pose detection
    private var poseDetector: PoseDetector? = null
    private val analysisBusy = AtomicBoolean(false)

    // Tilt sensor for the level line
    private var sensorManager: SensorManager? = null
    private var tiltListener: SensorEventListener? = null

    // Editor state
    private var editorBitmap: Bitmap? = null
    private var editorImage: ImageView? = null
    private var editorPreset: EffectPreset = Effects.default
    private var fxIntensity = 1f
    private var fxBrightness = 0f
    private var fxContrast = 1f
    private var fxSaturation = 1f
    private var fxWarmth = 0f

    // Pose photo library (real photos bundled in assets/poses)
    private data class PosePhoto(val file: String, val fa: String, val en: String, val category: String) {
        fun title(): String = I18n.s(fa, en)
    }
    private var posePhotos: List<PosePhoto>? = null
    private var poseFilter = "all"

    private val prefs by lazy { getSharedPreferences("zoomai", Context.MODE_PRIVATE) }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && currentTab == 0) showCamera()
        else if (!granted) toast(s("برای عکاسی اجازه دوربین ضروری است", "Camera permission is required"))
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        startRecording(withAudio = granted)
        if (!granted) toast(s("بدون صدا ضبط می‌شود", "Recording without sound"))
    }
    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            editorBitmap = decodeResized(uri)
            resetEffects()
            navigate(2)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.lang = if (prefs.getString("lang", "fa") == "en") Lang.EN else Lang.FA
        showGrid = prefs.getBoolean("grid_default", true)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutDirection = if (I18n.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        }
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(navigation(), LinearLayout.LayoutParams(-1, dp(60)))
        setContentView(root)
        navigate(0)
    }

    private fun navigation(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(panel)
        }
        val items = listOf(
            Triple(s("دوربین", "Camera"), R.drawable.ic_camera, 0),
            Triple(s("ژست‌ها", "Poses"), R.drawable.ic_pose, 1),
            Triple(s("ویرایش", "Edit"), R.drawable.ic_wand, 2),
            Triple(s("تنظیمات", "Settings"), R.drawable.ic_tune, 3)
        )
        for ((labelText, iconRes, index) in items) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setOnClickListener { navigate(index) }
            }
            val img = ImageView(this).apply { setImageResource(iconRes) }
            val lab = TextView(this).apply {
                text = labelText
                textSize = 10.5f
                gravity = Gravity.CENTER
            }
            navIcons.add(img)
            navLabels.add(lab)
            cell.addView(img, LinearLayout.LayoutParams(dp(23), dp(23)))
            cell.addView(lab, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            bar.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        }
        refreshNav()
        return bar
    }

    private fun refreshNav() {
        navIcons.forEachIndexed { i, img -> img.setColorFilter(if (i == currentTab) Color.WHITE else faint) }
        navLabels.forEachIndexed { i, lab -> lab.setTextColor(if (i == currentTab) Color.WHITE else faint) }
    }

    private fun navigate(tab: Int) {
        stopTiltSensor()
        if (isRecording) activeRecording?.stop()
        if (tab != 0) {
            cameraProvider?.unbindAll()
            poseOverlay?.setLive(null, 0, 0, 0, false)
        }
        currentTab = tab
        inAbout = false
        refreshNav()
        when (tab) {
            0 -> showCamera()
            1 -> showPoses()
            2 -> showEditor()
            else -> showSettings()
        }
    }

    // ------------------------------------------------------------------ camera screen

    private fun iconBtn(iconRes: Int, onClick: () -> Unit): IconBtn {
        val box = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(92, 0, 0, 0))
            }
            setOnClickListener { onClick() }
        }
        val img = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }
        box.addView(img, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        val badge = TextView(this).apply {
            textSize = 9f
            setTextColor(camAccent)
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        box.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(6), dp(4))
        })
        return IconBtn(box, img, badge)
    }

    private fun showCamera() {
        content.removeAllViews()
        btnFlash = null; btnTimer = null; btnGrid = null; btnTorch = null
        btnPose = null; btnLive = null; btnLevel = null
        modeLabels = mutableListOf(); zoomChips = mutableListOf()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            val prompt = vertical().apply { gravity = Gravity.CENTER; setPadding(dp(28), 0, dp(28), 0) }
            prompt.addView(title(s("دوربین Zoom AI Studio", "Zoom AI Studio Camera")))
            prompt.addView(note(s("برای پیش‌نمایش و گرفتن عکس، اجازه دسترسی به دوربین لازم است.", "Camera access is needed for preview and capture.")))
            prompt.addView(btnPrimary(s("اجازه دوربین", "Allow camera")) { cameraPermission.launch(Manifest.permission.CAMERA) },
                LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) })
            content.addView(prompt, FrameLayout.LayoutParams(-1, -1))
            return
        }
        val preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        content.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val ghost = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.45f
            visibility = View.GONE
        }
        ghostView = ghost
        content.addView(ghost, FrameLayout.LayoutParams(-1, -1))
        applyGhost()
        val overlay = PoseOverlayView(this).apply {
            template = null
            showFrame = showGrid
        }
        poseOverlay = overlay
        content.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val current = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val target = (current * detector.scaleFactor).coerceIn(minZoom, maxZoom)
                camera?.cameraControl?.setZoomRatio(target)
                return true
            }
        })
        preview.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.pointerCount == 1 && event.action == MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
                val point = preview.meteringPointFactory.createPoint(event.x, event.y)
                camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                overlay.showFocus(event.x, event.y)
            }
            true
        }

        // Top bar: two quiet rows of line icons, like a real camera app.
        val top = vertical().apply { setPadding(dp(10), dp(10), dp(10), 0) }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val gear = iconBtn(R.drawable.ic_tune) { navigate(3) }
        row1.addView(gear.box, LinearLayout.LayoutParams(dp(44), dp(44)))
        row1.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        btnFlash = iconBtn(R.drawable.ic_flash) {
            if (videoMode) toggleTorch() else {
                flashMode = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
                capture?.flashMode = flashMode
                refreshCameraUi()
            }
        }
        row1.addView(btnFlash!!.box, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        btnTimer = iconBtn(R.drawable.ic_timer) {
            timerSec = when (timerSec) { 0 -> 3; 3 -> 10; else -> 0 }
            refreshCameraUi()
        }
        row1.addView(btnTimer!!.box, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        btnGrid = iconBtn(R.drawable.ic_grid) {
            showGrid = !showGrid
            overlay.showFrame = showGrid
            refreshCameraUi()
        }
        row1.addView(btnGrid!!.box, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        ratioLabel = TextView(this).apply {
            text = "4:3"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.argb(92, 0, 0, 0))
                cornerRadius = dp(22).toFloat()
            }
            setOnClickListener {
                aspectRatio = if (aspectRatio == AspectRatio.RATIO_4_3) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
                bindCamera(preview)
                refreshCameraUi()
            }
        }
        row1.addView(ratioLabel, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        btnTorch = iconBtn(R.drawable.ic_torch) { toggleTorch() }
        row1.addView(btnTorch!!.box, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        top.addView(row1)
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(8), 0, 0)
        }
        btnPose = iconBtn(R.drawable.ic_pose) {
            poseStrip?.visibility = if (poseStrip?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            refreshCameraUi()
        }
        row2.addView(btnPose!!.box, LinearLayout.LayoutParams(dp(44), dp(44)))
        btnLive = iconBtn(R.drawable.ic_live) {
            livePoseOn = !livePoseOn
            if (!livePoseOn) overlay.setLive(null, 0, 0, 0, false)
            bindCamera(preview)
            refreshCameraUi()
        }
        row2.addView(btnLive!!.box, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        btnLevel = iconBtn(R.drawable.ic_level) {
            levelOn = !levelOn
            if (levelOn) startTiltSensor(overlay) else { stopTiltSensor(); overlay.levelDegrees = null }
            refreshCameraUi()
        }
        row2.addView(btnLevel!!.box, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        top.addView(row2)
        content.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Bottom stack: status, zoom pill, pose strip, pro panel, modes, shutter deck.
        val bottomStack = vertical().apply { gravity = Gravity.CENTER_HORIZONTAL }
        val status = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 15f
            setShadowLayer(dp(4).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
            setPadding(dp(16), 0, dp(16), dp(6))
        }
        statusLabel = status
        bottomStack.addView(status, LinearLayout.LayoutParams(-1, -2))
        zoomChipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val zoomScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(zoomChipRow)
        }
        bottomStack.addView(zoomScroll, LinearLayout.LayoutParams(-2, dp(40)))
        poseStrip = buildPoseStrip()
        bottomStack.addView(poseStrip, LinearLayout.LayoutParams(-1, dp(86)))
        proPanel = buildProPanel()
        bottomStack.addView(proPanel, LinearLayout.LayoutParams(-1, -2))
        val deck = vertical().apply { setBackgroundColor(Color.argb(232, 0, 0, 0)) }
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        }
        val modes = listOf(
            2 to s("حرفه‌ای", "Pro"),
            0 to s("عکس", "Photo"),
            1 to s("ویدیو", "Video")
        )
        for ((mode, labelText) in modes) {
            val tv = TextView(this).apply {
                text = labelText
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(6), dp(16), dp(6))
                tag = mode
                setOnClickListener { setMode(mode, preview) }
            }
            modeLabels.add(tv)
            modeRow.addView(tv, LinearLayout.LayoutParams(-2, -2))
        }
        deck.addView(modeRow)
        val shutterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(30), dp(8), dp(30), dp(16))
        }
        val galleryBtn = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(40, 255, 255, 255))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.argb(140, 255, 255, 255))
            }
            setOnClickListener { openGallery() }
        }
        val galleryImg = ImageView(this).apply { setImageResource(R.drawable.ic_gallery); setColorFilter(Color.WHITE) }
        galleryBtn.addView(galleryImg, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        shutterRow.addView(galleryBtn, LinearLayout.LayoutParams(dp(46), dp(46)))
        shutterRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val shutter = FrameLayout(this).apply { setOnClickListener { onShutter(preview) } }
        val ring = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), Color.WHITE)
            }
        }
        shutter.addView(ring, FrameLayout.LayoutParams(dp(78), dp(78), Gravity.CENTER))
        val inner = View(this)
        shutterInner = inner
        shutter.addView(inner, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
        shutterRow.addView(shutter, LinearLayout.LayoutParams(dp(84), dp(84)))
        shutterRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val switchBtn = iconBtn(R.drawable.ic_switch) {
            if (isRecording) return@iconBtn toast(s("هنگام ضبط نمی‌شود دوربین را عوض کرد", "Cannot switch camera while recording"))
            backCamera = !backCamera
            bindCamera(preview)
        }
        shutterRow.addView(switchBtn.box, LinearLayout.LayoutParams(dp(48), dp(48)))
        deck.addView(shutterRow)
        bottomStack.addView(deck, LinearLayout.LayoutParams(-1, -2))
        content.addView(bottomStack, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        if (levelOn) startTiltSensor(overlay)
        refreshCameraUi()
        bindCamera(preview)
    }

    private fun buildProPanel(): LinearLayout {
        val p = vertical().apply {
            setBackgroundColor(Color.argb(168, 8, 10, 14))
            setPadding(dp(20), dp(8), dp(20), dp(8))
            visibility = View.GONE
        }
        val evRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        evRow.addView(TextView(this).apply { text = "EV"; setTextColor(muted); textSize = 12f }, LinearLayout.LayoutParams(dp(44), -2))
        val evSeek = SeekBar(this)
        evSeekBar = evSeek
        evSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val cam = camera ?: return
                val state = cam.cameraInfo.exposureState
                if (!state.isExposureCompensationSupported) return
                val index = state.exposureCompensationRange.lower + progress
                cam.cameraControl.setExposureCompensationIndex(index)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        evRow.addView(evSeek, LinearLayout.LayoutParams(0, -2, 1f))
        p.addView(evRow)
        val zRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        zRow.addView(TextView(this).apply { text = s("زوم", "Zoom"); setTextColor(muted); textSize = 12f }, LinearLayout.LayoutParams(dp(44), -2))
        val zSeek = SeekBar(this).apply { max = 100 }
        zoomSeek = zSeek
        zSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && maxZoom > minZoom) {
                    camera?.cameraControl?.setZoomRatio(minZoom + (maxZoom - minZoom) * progress / 100f)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        zRow.addView(zSeek, LinearLayout.LayoutParams(0, -2, 1f))
        p.addView(zRow)
        return p
    }

    private fun buildPoseStrip(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val clear = TextView(this).apply {
            text = s("بدون ژست", "No pose")
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            background = GradientDrawable().apply { setColor(Color.argb(120, 16, 20, 27)); cornerRadius = dp(10).toFloat() }
            setOnClickListener {
                ghostPoseFile = null
                ghostPoseTitle = null
                applyGhost()
                refreshCameraUi()
            }
        }
        row.addView(clear, LinearLayout.LayoutParams(dp(64), dp(64)))
        for (photo in loadPosePhotos()) {
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener {
                    ghostPoseFile = photo.file
                    ghostPoseTitle = photo.title()
                    applyGhost()
                    refreshCameraUi()
                }
            }
            decodeAssetInto(thumb, photo.file, 6)
            row.addView(thumb, LinearLayout.LayoutParams(dp(50), dp(64)).apply { marginStart = dp(8) })
        }
        val all = TextView(this).apply {
            text = s("همه ژست‌ها", "All poses")
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            background = GradientDrawable().apply { setColor(Color.argb(120, 16, 20, 27)); cornerRadius = dp(10).toFloat() }
            setOnClickListener { navigate(1) }
        }
        row.addView(all, LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginStart = dp(8) })
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setBackgroundColor(Color.argb(150, 8, 10, 14))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(row)
            visibility = View.GONE
        }
        return scroll
    }

    private fun setMode(mode: Int, preview: PreviewView) {
        if (isRecording) return
        videoMode = mode == 1
        proMode = mode == 2
        bindCamera(preview)
        refreshCameraUi()
    }

    private fun refreshCameraUi() {
        fun tint(btn: IconBtn?, active: Boolean) {
            btn?.img?.setColorFilter(if (active) camAccent else Color.WHITE)
        }
        tint(btnFlash, flashMode != ImageCapture.FLASH_MODE_OFF)
        btnFlash?.badge?.let {
            it.text = "A"
            it.visibility = if (flashMode == ImageCapture.FLASH_MODE_AUTO) View.VISIBLE else View.GONE
        }
        tint(btnTimer, timerSec > 0)
        btnTimer?.badge?.let {
            it.text = if (timerSec > 0) timerSec.toString() else ""
            it.visibility = if (timerSec > 0) View.VISIBLE else View.GONE
        }
        tint(btnGrid, showGrid)
        tint(btnTorch, torchOn)
        tint(btnPose, ghostPoseFile != null || poseStrip?.visibility == View.VISIBLE)
        tint(btnLive, livePoseOn)
        tint(btnLevel, levelOn)
        ratioLabel?.text = if (aspectRatio == AspectRatio.RATIO_4_3) "4:3" else "16:9"
        val currentMode = when { videoMode -> 1; proMode -> 2; else -> 0 }
        for (tv in modeLabels) {
            val selected = (tv.tag as? Int) == currentMode
            tv.setTextColor(if (selected) Color.WHITE else faint)
            tv.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        proPanel?.visibility = if (proMode) View.VISIBLE else View.GONE
        shutterInner?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(when {
                isRecording -> danger
                videoMode -> danger
                else -> Color.WHITE
            })
        }
        if (!isRecording && !countingDown) statusLabel?.text = ghostPoseTitle ?: ""
    }

    private fun fmtZoom(r: Float): String {
        val t = "%.1f".format(Locale.US, r)
        val base = if (t.endsWith(".0")) t.dropLast(2) else t
        return base.removePrefix("0") + "×"
    }

    private fun rebuildZoomChips() {
        val row = zoomChipRow ?: return
        row.removeAllViews()
        zoomChips.clear()
        val candidates = listOf(minZoom, 1f, 2f, 3f, 5f, 10f, maxZoom)
            .filter { it >= minZoom - 0.01f && it <= maxZoom + 0.01f }
            .distinctBy { fmtZoom(it) }
            .sorted()
        for (ratio in candidates) {
            val tv = TextView(this).apply {
                text = fmtZoom(ratio)
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(dp(11), dp(4), dp(11), dp(4))
                setOnClickListener { camera?.cameraControl?.setZoomRatio(ratio) }
            }
            zoomChips.add(ratio to tv)
            row.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(5); marginEnd = dp(5) })
        }
        highlightZoomChips(camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f)
    }

    private fun highlightZoomChips(current: Float) {
        if (zoomChips.isEmpty()) return
        val nearest = zoomChips.minByOrNull { kotlin.math.abs(it.first - current) }?.first
        for ((ratio, tv) in zoomChips) {
            val selected = ratio == nearest
            tv.setTextColor(if (selected) Color.BLACK else Color.WHITE)
            tv.background = GradientDrawable().apply {
                setColor(if (selected) Color.WHITE else Color.argb(115, 14, 18, 24))
                cornerRadius = dp(13).toFloat()
            }
        }
    }

    private fun setupEvSeek(seek: SeekBar) {
        val cam = camera ?: return
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        seek.max = state.exposureCompensationRange.upper - state.exposureCompensationRange.lower
        seek.progress = state.exposureCompensationIndex - state.exposureCompensationRange.lower
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        refreshCameraUi()
    }

    private fun openGallery() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"))
        } catch (e: Exception) {
            toast(s("گالری باز نشد", "Could not open the gallery"))
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                if (currentTab != 0 || content.childCount == 0) return@addListener
                val provider = future.get()
                cameraProvider = provider
                provider.unbindAll()
                val selector = if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                val preview = Preview.Builder().setTargetAspectRatio(aspectRatio).build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)
                if (videoMode) {
                    if (recorder == null) {
                        recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                            .build()
                    }
                    val vc = VideoCapture.withOutput(recorder!!)
                    videoCapture = vc
                    capture = null
                    useCases.add(vc)
                } else {
                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetAspectRatio(aspectRatio)
                        .build()
                    imageCapture.flashMode = flashMode
                    capture = imageCapture
                    videoCapture = null
                    useCases.add(imageCapture)
                    if (livePoseOn) {
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetAspectRatio(aspectRatio)
                            .build()
                        analysis.setAnalyzer(cameraExecutor, poseAnalyzer)
                        useCases.add(analysis)
                    }
                }
                camera = provider.bindToLifecycle(this, selector, *useCases.toTypedArray())
                if (torchOn) camera?.cameraControl?.enableTorch(true)
                evSeekBar?.let { setupEvSeek(it) }
                camera?.cameraInfo?.zoomState?.observe(this) { state ->
                    if (state != null) {
                        if (state.minZoomRatio != minZoom || state.maxZoomRatio != maxZoom) {
                            minZoom = state.minZoomRatio
                            maxZoom = state.maxZoomRatio
                            rebuildZoomChips()
                        }
                        highlightZoomChips(state.zoomRatio)
                        if (maxZoom > minZoom) {
                            zoomSeek?.progress = ((state.zoomRatio - minZoom) / (maxZoom - minZoom) * 100).toInt()
                        }
                    }
                }
            } catch (e: Exception) {
                toast(s("راه‌اندازی دوربین ممکن نشد", "Camera failed to start") + ": ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ------------------------------------------------------------------ pose detection

    private fun detector(): PoseDetector {
        if (poseDetector == null) {
            poseDetector = PoseDetection.getClient(
                PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
            )
        }
        return poseDetector!!
    }

    private val poseAnalyzer = ImageAnalysis.Analyzer { proxy ->
        val media = proxy.image
        if (media == null || !livePoseOn) {
            proxy.close()
            return@Analyzer
        }
        if (!analysisBusy.compareAndSet(false, true)) {
            proxy.close()
            return@Analyzer
        }
        val rotation = proxy.imageInfo.rotationDegrees
        val input = InputImage.fromMediaImage(media, rotation)
        val width = proxy.width
        val height = proxy.height
        val front = !backCamera
        detector().process(input)
            .addOnSuccessListener { pose ->
                val points = arrayOfNulls<PointF>(33)
                for (lm in pose.getAllPoseLandmarks()) {
                    if (lm.landmarkType in 0..32) points[lm.landmarkType] = lm.position
                }
                val hasPerson = points.any { it != null }
                runOnUiThread {
                    poseOverlay?.setLive(if (hasPerson) points else null, width, height, rotation, front)
                }
            }
            .addOnCompleteListener {
                analysisBusy.set(false)
                proxy.close()
            }
    }

    // ------------------------------------------------------------------ tilt level

    private fun startTiltSensor(overlay: PoseOverlayView) {
        stopTiltSensor()
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                val roll = Math.toDegrees(Math.atan2(x.toDouble(), y.toDouble())).toFloat()
                overlay.levelDegrees = roll
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        tiltListener = listener
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopTiltSensor() {
        val sm = sensorManager
        val listener = tiltListener
        if (sm != null && listener != null) sm.unregisterListener(listener)
        tiltListener = null
    }

    // ------------------------------------------------------------------ capture

    private fun onShutter(preview: PreviewView) {
        if (videoMode) {
            if (isRecording) stopRecording() else requestAudioAndRecord()
            return
        }
        if (countingDown) return
        if (timerSec > 0) {
            countingDown = true
            object : CountDownTimer(timerSec * 1000L, 1000L) {
                override fun onTick(ms: Long) {
                    statusLabel?.text = s("آماده…", "Get ready…") + " " + (ms / 1000 + 1)
                }
                override fun onFinish() {
                    countingDown = false
                    statusLabel?.text = ghostPoseTitle ?: ""
                    takePhoto()
                }
            }.start()
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val useCase = capture ?: return toast(s("دوربین هنوز آماده نیست", "Camera is not ready yet"))
        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues()
        ).build()
        useCase.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                runOnUiThread { toast(s("عکس در گالری گوشی ذخیره شد", "Photo saved to your gallery")) }
            }
            override fun onError(exc: ImageCaptureException) {
                runOnUiThread { toast(s("خطا هنگام عکس‌برداری", "Capture error") + ": ${exc.localizedMessage}") }
            }
        })
    }

    // ------------------------------------------------------------------ video

    private fun requestAudioAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording(withAudio = true)
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording(withAudio: Boolean) {
        val vc = videoCapture ?: return toast(s("حالت ویدیو آماده نیست", "Video mode is not ready"))
        val values = ContentValues().apply {
            val date = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            put(MediaStore.Video.Media.DISPLAY_NAME, "ZoomAI_$date.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ZoomAIStudio")
            }
        }
        val output = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()
        var pending = vc.output.prepareRecording(this, output)
        if (withAudio) pending = pending.withAudioEnabled()
        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true
                    recordStartedAt = System.currentTimeMillis()
                    mainHandler.post(recordTicker)
                    refreshCameraUi()
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    activeRecording = null
                    mainHandler.removeCallbacks(recordTicker)
                    statusLabel?.text = ghostPoseTitle ?: ""
                    statusLabel?.setTextColor(Color.WHITE)
                    refreshCameraUi()
                    if (event.hasError()) toast(s("خطا در ضبط ویدیو", "Video recording error"))
                    else toast(s("ویدیو در گالری ذخیره شد", "Video saved to your gallery"))
                }
            }
        }
    }

    private val recordTicker = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val sec = (System.currentTimeMillis() - recordStartedAt) / 1000
            statusLabel?.text = "● " + "%02d:%02d".format(Locale.US, sec / 60, sec % 60)
            statusLabel?.setTextColor(danger)
            mainHandler.postDelayed(this, 500)
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
    }

    // ------------------------------------------------------------------ pose photo library

    private fun loadPosePhotos(): List<PosePhoto> {
        posePhotos?.let { return it }
        val list = mutableListOf<PosePhoto>()
        try {
            val text = assets.open("poses/manifest.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PosePhoto(
                        file = o.getString("file"),
                        fa = o.optString("title_fa"),
                        en = o.optString("title_en"),
                        category = o.optString("category")
                    )
                )
            }
        } catch (e: Exception) {
            // Library stays empty if the manifest is missing; the camera still works.
        }
        posePhotos = list
        return list
    }

    private fun decodeAsset(file: String, sample: Int): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        assets.open("poses/$file").use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }

    private fun decodeAssetInto(img: ImageView, file: String, sample: Int) {
        val bmp = decodeAsset(file, sample) ?: return
        img.setImageBitmap(bmp)
    }

    private fun applyGhost() {
        val gv = ghostView ?: return
        val file = ghostPoseFile
        if (file == null) {
            gv.visibility = View.GONE
            gv.setImageBitmap(null)
        } else {
            val bmp = decodeAsset(file, 2)
            if (bmp != null) {
                gv.setImageBitmap(bmp)
                gv.visibility = View.VISIBLE
            }
        }
    }

    private fun showPoses() {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val layout = vertical().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        layout.addView(title(s("ژست‌ها", "Poses")))
        layout.addView(note(s("عکس واقعی هر ژست است. روی یک عکس بزن تا مثل سایه روی دوربین بیاید و همان ژست را بگیری.",
            "Each pose is a real photo. Tap one and it appears as a ghost over the camera so you can match it.")))
        val categories = listOf(
            Triple("all", "همه", "All"),
            Triple("women", "زنانه", "Women"),
            Triple("men", "مردانه", "Men"),
            Triple("couple", "دو نفره", "Couples"),
            Triple("family", "خانواده", "Family"),
            Triple("kids", "کودکان", "Kids"),
            Triple("friends", "دوستان", "Friends")
        )
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((key, fa, en) in categories) {
            val selected = poseFilter == key
            val chipTv = TextView(this).apply {
                text = I18n.s(fa, en)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(7), dp(14), dp(7))
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (selected) Color.WHITE else card)
                    cornerRadius = dp(16).toFloat()
                }
                setOnClickListener { poseFilter = key; showPoses() }
            }
            chipRow.addView(chipTv, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        layout.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(6) })

        val photos = loadPosePhotos().filter { poseFilter == "all" || it.category == poseFilter }
        if (photos.isEmpty()) {
            layout.addView(note(s("در این دسته هنوز عکسی نیست.", "No photos in this category yet.")))
        }
        for (pair in photos.chunked(2)) {
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (photo in pair) {
                val isSelected = ghostPoseFile == photo.file
                val cardBox = vertical().apply {
                    setPadding(dp(5), dp(5), dp(5), dp(8))
                    background = GradientDrawable().apply {
                        setColor(panel)
                        cornerRadius = dp(14).toFloat()
                        if (isSelected) setStroke(dp(2), accent)
                    }
                    setOnClickListener {
                        ghostPoseFile = photo.file
                        ghostPoseTitle = photo.title()
                        navigate(0)
                        toast(s("ژست روی دوربین آمد", "Pose is on the camera"))
                    }
                }
                val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                val bmp = decodeAsset(photo.file, 3)
                if (bmp != null) {
                    val rounded = RoundedBitmapDrawableFactory.create(resources, bmp)
                    rounded.cornerRadius = dp(10).toFloat()
                    rounded.setAntiAlias(true)
                    img.setImageDrawable(rounded)
                }
                cardBox.addView(img, LinearLayout.LayoutParams(-1, dp(190)))
                cardBox.addView(TextView(this).apply {
                    text = photo.title()
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setTextColor(if (isSelected) accent else Color.WHITE)
                    setPadding(dp(4), dp(7), dp(4), 0)
                }, LinearLayout.LayoutParams(-1, -2))
                rowLayout.addView(cardBox, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4); topMargin = dp(8)
                })
            }
            if (pair.size == 1) rowLayout.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                marginStart = dp(4); marginEnd = dp(4)
            })
            layout.addView(rowLayout)
        }
        layout.addView(note(s("مجموعهٔ آغازین: ۱۸ عکس واقعی با مجوز آزاد (Pexels). عکس‌های بیشتر بعداً از اینترنت گرفته و روی گوشی ذخیره می‌شوند.",
            "Starter set: 18 real photos, free license (Pexels). More will be downloaded and cached on the phone later.")),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        scroll.addView(layout)
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    // ------------------------------------------------------------------ editor

    private fun resetEffects() {
        editorPreset = Effects.default
        fxIntensity = 1f
        fxBrightness = 0f
        fxContrast = 1f
        fxSaturation = 1f
        fxWarmth = 0f
    }

    private fun applyEditorFilter() {
        val img = editorImage ?: return
        if (editorBitmap == null) {
            img.colorFilter = null
            return
        }
        img.colorFilter = ColorMatrixColorFilter(
            Effects.compose(editorPreset, fxIntensity, fxBrightness, fxContrast, fxSaturation, fxWarmth)
        )
    }

    private fun sliderRow(labelText: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = labelText
            setTextColor(muted)
            textSize = 13f
        }, LinearLayout.LayoutParams(dp(86), -2))
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = value - min
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) onChange(progress + min)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        row.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun showEditor() {
        content.removeAllViews()
        val layout = vertical().apply { setPadding(dp(16), dp(14), dp(16), dp(14)) }
        layout.addView(title(s("ویرایشگر عکس", "Photo Editor")))
        layout.addView(btnGhost(s("انتخاب عکس از گالری", "Pick a photo from gallery")) { imagePicker.launch("image/*") },
            LinearLayout.LayoutParams(-1, dp(48)))
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(panel)
            setImageBitmap(editorBitmap)
        }
        editorImage = img
        layout.addView(img, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12); bottomMargin = dp(12) })

        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for (preset in Effects.presets) {
            val selected = preset == editorPreset
            val chipTv = TextView(this).apply {
                text = preset.name()
                textSize = 12.5f
                gravity = Gravity.CENTER
                setPadding(dp(13), dp(7), dp(13), dp(7))
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (selected) Color.WHITE else card)
                    cornerRadius = dp(16).toFloat()
                }
                setOnClickListener {
                    editorPreset = preset
                    showEditor()
                }
            }
            presetRow.addView(chipTv, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(7) })
        }
        layout.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(presetRow)
        }, LinearLayout.LayoutParams(-1, dp(44)))

        val sliders = vertical().apply {
            setBackgroundColor(panel)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        sliders.addView(sliderRow(s("شدت افکت", "Intensity"), 0, 100, (fxIntensity * 100).toInt()) { v -> fxIntensity = v / 100f; applyEditorFilter() })
        sliders.addView(sliderRow(s("روشنایی", "Brightness"), -60, 60, fxBrightness.toInt()) { v -> fxBrightness = v.toFloat(); applyEditorFilter() })
        sliders.addView(sliderRow(s("کنتراست", "Contrast"), 50, 150, (fxContrast * 100).toInt()) { v -> fxContrast = v / 100f; applyEditorFilter() })
        sliders.addView(sliderRow(s("اشباع رنگ", "Saturation"), 0, 200, (fxSaturation * 100).toInt()) { v -> fxSaturation = v / 100f; applyEditorFilter() })
        sliders.addView(sliderRow(s("گرمای رنگ", "Warmth"), -50, 50, fxWarmth.toInt()) { v -> fxWarmth = v.toFloat(); applyEditorFilter() })
        layout.addView(ScrollView(this).apply { addView(sliders) }, LinearLayout.LayoutParams(-1, dp(172)))

        val editRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(10))
        }
        editRow.addView(btnGhost(s("چرخش", "Rotate")) { transformBitmap(rotate = true) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        editRow.addView(btnGhost(s("برگردان", "Flip")) { transformBitmap(rotate = false) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
        editRow.addView(btnGhost(s("بازنشانی", "Reset")) { resetEffects(); showEditor() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        layout.addView(editRow)
        layout.addView(btnPrimary(s("ذخیره نسخه ویرایش‌شده", "Save edited copy")) { saveEditedCopy() },
            LinearLayout.LayoutParams(-1, dp(52)))
        layout.addView(note(s("فایل اصلی بدون تغییر باقی می‌ماند؛ نسخهٔ تازه جدا ذخیره می‌شود.",
            "The original file is never changed; the edited copy is saved separately.")))
        content.addView(layout, FrameLayout.LayoutParams(-1, -1))
        applyEditorFilter()
    }

    private fun transformBitmap(rotate: Boolean) {
        val source = editorBitmap ?: return toast(s("ابتدا عکس انتخاب کنید", "Pick a photo first"))
        val matrix = Matrix()
        if (rotate) matrix.postRotate(90f) else matrix.postScale(-1f, 1f, source.width / 2f, source.height / 2f)
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) {
            editorBitmap = result
            editorImage?.setImageBitmap(result)
        }
    }

    private fun saveEditedCopy() {
        val source = editorBitmap ?: return toast(s("ابتدا عکس انتخاب کنید", "Pick a photo first"))
        val matrix = Effects.compose(editorPreset, fxIntensity, fxBrightness, fxContrast, fxSaturation, fxWarmth)
        cameraExecutor.execute {
            try {
                val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
                canvas.drawBitmap(source, 0f, 0f, paint)
                val values = ContentValues().apply {
                    val date = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
                    put(MediaStore.Images.Media.DISPLAY_NAME, "ZoomAI_Edit_$date.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZoomAIStudio")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("create failed")
                contentResolver.openOutputStream(uri)?.use { stream ->
                    if (!output.compress(Bitmap.CompressFormat.JPEG, 94, stream)) error("write failed")
                } ?: error("open failed")
                output.recycle()
                runOnUiThread { toast(s("نسخه جدید در گالری ذخیره شد", "Edited copy saved to your gallery")) }
            } catch (e: Exception) {
                runOnUiThread { toast(s("خطا", "Error") + ": ${e.localizedMessage}") }
            }
        }
    }

    // ------------------------------------------------------------------ settings & about

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 12.5f
        setTextColor(faint)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun settingRow(titleText: String, subText: String?, end: View?): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(13), 0, dp(13))
        }
        val texts = vertical()
        texts.addView(TextView(this).apply {
            text = titleText
            textSize = 15.5f
            setTextColor(Color.WHITE)
        })
        if (subText != null) texts.addView(TextView(this).apply {
            text = subText
            textSize = 12.5f
            setTextColor(muted)
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (end != null) row.addView(end, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun divider() = View(this).apply { setBackgroundColor(card) }

    private fun showSettings() {
        content.removeAllViews()
        val scroll = ScrollView(this)
        val layout = vertical().apply { setPadding(dp(18), dp(14), dp(18), dp(18)) }
        layout.addView(title(s("تنظیمات", "Settings")))
        layout.addView(sectionLabel(s("عمومی", "General")))
        val langBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(card)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = GradientDrawable().apply { setColor(card); cornerRadius = dp(12).toFloat() }
        }
        for ((lang, lab) in listOf(Lang.FA to "فارسی", Lang.EN to "English")) {
            val selected = I18n.lang == lang
            val pill = TextView(this).apply {
                text = lab
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(6), dp(14), dp(6))
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(if (selected) Color.WHITE else Color.TRANSPARENT)
                    cornerRadius = dp(9).toFloat()
                }
                setOnClickListener { if (!selected) setLanguage(lang) }
            }
            langBox.addView(pill, LinearLayout.LayoutParams(-2, -2))
        }
        layout.addView(settingRow(s("زبان برنامه", "App language"), null, langBox))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        val gridSwitch = Switch(this).apply {
            isChecked = showGrid
            setOnCheckedChangeListener { _, checked ->
                showGrid = checked
                prefs.edit().putBoolean("grid_default", checked).apply()
            }
        }
        layout.addView(settingRow(
            s("شبکه راهنما", "Guide grid"),
            s("خطوط یک‌سوم هنگام باز شدن دوربین روشن باشد", "Show the rule-of-thirds grid when the camera opens"),
            gridSwitch
        ))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        layout.addView(settingRow(
            s("محل ذخیره‌سازی", "Storage"),
            s("عکس‌ها: Pictures/ZoomAIStudio — ویدیوها: Movies/ZoomAIStudio", "Photos: Pictures/ZoomAIStudio — Videos: Movies/ZoomAIStudio"),
            null
        ))
        layout.addView(sectionLabel(s("برنامه", "App")))
        layout.addView(settingRow(s("نسخه", "Version"), "0.3.0", null))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        val aboutEnd = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(faint)
        }
        val aboutRow = settingRow(s("درباره ما", "About us"), s("سازنده و راه‌های تماس", "Developer and contact links"), aboutEnd)
        aboutRow.setOnClickListener { showAbout() }
        layout.addView(aboutRow)
        scroll.addView(layout)
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun aboutRow(labelText: String, valueText: String, url: String): LinearLayout {
        val end = TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(faint)
        }
        val row = settingRow(labelText, valueText, end)
        row.setOnClickListener { openLink(url) }
        return row
    }

    private fun showAbout() {
        inAbout = true
        content.removeAllViews()
        val scroll = ScrollView(this)
        val layout = vertical().apply { setPadding(dp(18), dp(12), dp(18), dp(20)) }
        val backRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { inAbout = false; showSettings() }
        }
        val backImg = ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(Color.WHITE)
            if (!I18n.isRtl) rotation = 180f
        }
        backRow.addView(backImg, LinearLayout.LayoutParams(dp(20), dp(20)))
        backRow.addView(TextView(this).apply {
            text = s("بازگشت", "Back")
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(6), 0, dp(6), 0)
        })
        layout.addView(backRow)
        val iconWrap = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(10)) }
        iconWrap.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) },
            LinearLayout.LayoutParams(dp(86), dp(86)))
        layout.addView(iconWrap)
        layout.addView(TextView(this).apply {
            text = "Zoom AI Studio"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        layout.addView(TextView(this).apply {
            text = s("نسخه 0.3.0", "Version 0.3.0")
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
        layout.addView(sectionLabel(s("سازنده", "Developer")))
        layout.addView(TextView(this).apply {
            text = "Noorullah Haidari"
            textSize = 17f
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = s("توسعه‌دهندهٔ فول‌استک وب و موبایل — کابل، افغانستان", "Full-stack web & mobile developer — Kabul, Afghanistan")
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(3), 0, dp(6))
        })
        layout.addView(aboutRow(s("وب‌سایت من", "My website"), "noorcode.tech", "https://noorcode.tech"))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        layout.addView(aboutRow("GitHub", "haidarzada987-tech", "https://github.com/haidarzada987-tech"))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        layout.addView(aboutRow("LinkedIn", "Noorullah Haidari", "https://www.linkedin.com/in/noorullah-haidari-44863142b"))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        layout.addView(aboutRow("Instagram", "noo_rullha", "https://www.instagram.com/noo_rullha"))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        layout.addView(aboutRow("WhatsApp", "+93 748 767 632", "https://wa.me/93748767632"))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        layout.addView(aboutRow(s("ایمیل", "Email"), "haidarzada987@gmail.com", "mailto:haidarzada987@gmail.com"))
        layout.addView(sectionLabel(s("برنامه‌ها و پروژه‌های دیگر من", "My other apps & projects")))
        layout.addView(aboutRow("BMI Pro", s("برنامهٔ BMI و تندرستی", "BMI & wellness app"), "https://github.com/haidarzada987-tech/BMI-Pro"))
        layout.addView(divider(), LinearLayout.LayoutParams(-1, 1))
        layout.addView(aboutRow("Toba Studio", s("پلتفرم خدمات دیجیتال", "Digital services platform"), "https://github.com/haidarzada987-tech/Toba-Studio"))
        layout.addView(note(s("برای سفارش وب‌سایت یا برنامه، از واتساپ یا ایمیل پیام بدهید.",
            "For website or app orders, reach me on WhatsApp or email.")).apply {
            setPadding(0, dp(16), 0, 0)
        })
        layout.addView(note(s("حریم خصوصی: عکس‌ها و ویدیوها فقط روی گوشی شما می‌مانند و تشخیص ژست روی خود گوشی انجام می‌شود.",
            "Privacy: photos and videos stay on your phone, and pose detection runs on this device.")))
        scroll.addView(layout)
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun setLanguage(lang: Lang) {
        prefs.edit().putString("lang", if (lang == Lang.EN) "en" else "fa").apply()
        recreate()
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast(s("لینک باز نشد", "Could not open the link"))
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun imageValues(): ContentValues = ContentValues().apply {
        val date = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        put(MediaStore.Images.Media.DISPLAY_NAME, "ZoomAI_$date.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ZoomAIStudio")
        }
    }

    private fun decodeResized(uri: Uri): Bitmap? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val maxDimension = maxOf(options.outWidth, options.outHeight)
        var sample = 1
        while (maxDimension / sample > 2400) sample *= 2
        options.inJustDecodeBounds = false
        options.inSampleSize = sample
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    } catch (e: Exception) {
        toast(s("عکس باز نشد", "Could not open the photo") + ": ${e.localizedMessage}")
        null
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); setPadding(0, dp(6), 0, dp(6))
    }
    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 21f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(10), 0, dp(10))
    }
    private fun note(value: String) = label(value, 13f, muted).apply { setLineSpacing(dp(3).toFloat(), 1f) }
    private fun btnPrimary(value: String, task: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(14).toFloat() }
        setOnClickListener { task() }
    }
    private fun btnGhost(value: String, task: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(card); cornerRadius = dp(12).toFloat() }
        setOnClickListener { task() }
    }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        stopTiltSensor()
        if (isRecording) activeRecording?.stop()
        mainHandler.removeCallbacks(recordTicker)
        cameraProvider?.unbindAll()
        poseDetector?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
