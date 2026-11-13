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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Zoom AI Studio 0.2.0 — local-first camera studio.
 * Camera, video, ML Kit pose detection and the editor all run on-device;
 * there is no server and no upload. Cloud AI remains a planned module.
 */
class MainActivity : AppCompatActivity() {
    private val bg = Color.rgb(8, 12, 22)
    private val panel = Color.rgb(23, 31, 45)
    private val accent = Color.rgb(87, 219, 176)
    private val danger = Color.rgb(255, 92, 92)
    private val muted = Color.rgb(174, 186, 202)

    private fun s(fa: String, en: String) = I18n.s(fa, en)

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private var currentTab = 0

    // Camera state
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var capture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recorder: Recorder? = null
    private var activeRecording: Recording? = null
    private var isRecording = false
    private var videoMode = false
    private var backCamera = true
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var torchOn = false
    private var showGrid = true
    private var livePoseOn = false
    private var levelOn = false
    private var timerSec = 0
    private var countingDown = false
    private var selectedPose: PoseTemplate? = null
    private var poseOverlay: PoseOverlayView? = null
    private var statusLabel: TextView? = null
    private var zoomSeek: SeekBar? = null
    private var evSeekBar: SeekBar? = null
    private var minZoom = 1f
    private var maxZoom = 1f
    private var recordStartedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

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

    // Poses filter
    private var poseFilterFa: String? = null

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
            currentTab = 2
            showEditor()
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
        root.addView(navigation(), LinearLayout.LayoutParams(-1, dp(66)))
        setContentView(root)
        navigate(0)
    }

    private fun navigation(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(panel)
        }
        listOf(
            s("دوربین", "Camera") to "◉",
            s("ژست‌ها", "Poses") to "♧",
            s("ویرایش", "Edit") to "✦",
            s("تنظیمات", "Settings") to "⚙"
        ).forEachIndexed { index, (label, icon) ->
            val b = TextView(this).apply {
                text = "$icon\n$label"
                gravity = Gravity.CENTER
                textSize = 13f
                setTextColor(if (currentTab == index) accent else Color.WHITE)
                setOnClickListener { navigate(index) }
            }
            bar.addView(b, LinearLayout.LayoutParams(0, -1, 1f))
        }
        return bar
    }

    private fun navigate(tab: Int) {
        stopTiltSensor()
        if (isRecording) activeRecording?.stop()
        if (tab != 0) {
            cameraProvider?.unbindAll()
            poseOverlay?.setLive(null, 0, 0, 0, false)
        }
        currentTab = tab
        root.removeViewAt(root.childCount - 1)
        root.addView(navigation(), LinearLayout.LayoutParams(-1, dp(66)))
        when (tab) {
            0 -> showCamera()
            1 -> showPoses()
            2 -> showEditor()
            else -> showSettings()
        }
    }

    // ------------------------------------------------------------------ camera

    private fun showCamera() {
        content.removeAllViews()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            val prompt = vertical().apply { gravity = Gravity.CENTER; setPadding(dp(24), 0, dp(24), 0) }
            prompt.addView(title(s("دوربین حرفه‌ای Zoom AI Studio", "Zoom AI Studio Pro Camera")))
            prompt.addView(note(s("برای پیش‌نمایش و گرفتن عکس، اجازه دسترسی به دوربین لازم است.", "Camera access is needed for preview and capture.")))
            prompt.addView(action(s("اجازه دوربین", "Allow camera")) { cameraPermission.launch(Manifest.permission.CAMERA) })
            content.addView(prompt, FrameLayout.LayoutParams(-1, -1))
            return
        }
        val preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        content.addView(preview, FrameLayout.LayoutParams(-1, -1))
        val overlay = PoseOverlayView(this).apply {
            template = selectedPose
            showFrame = showGrid
        }
        poseOverlay = overlay
        content.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        // Touch: pinch to zoom, tap to focus.
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val current = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val target = (current * detector.scaleFactor).coerceIn(minZoom, maxZoom)
                camera?.cameraControl?.setZoomRatio(target)
                syncZoomSeek(target)
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

        // Top controls, two rows.
        val top = vertical().apply { setPadding(dp(8), dp(10), dp(8), dp(4)) }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(compact(s("⇄ لنز", "⇄ Lens")) {
            if (isRecording) return@compact toast(s("هنگام ضبط نمی‌شود لنز را عوض کرد", "Cannot switch lens while recording"))
            backCamera = !backCamera
            bindCamera(preview)
        }, LinearLayout.LayoutParams(0, dp(45), 1f))
        row1.addView(compact(s("⚡ فلش", "⚡ Flash")) {
            if (videoMode) {
                toggleTorch()
            } else {
                flashMode = when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
                capture?.flashMode = flashMode
                toast(s("فلش", "Flash") + ": " + when (flashMode) {
                    ImageCapture.FLASH_MODE_OFF -> s("خاموش", "Off")
                    ImageCapture.FLASH_MODE_ON -> s("روشن", "On")
                    else -> s("خودکار", "Auto")
                })
            }
        }, LinearLayout.LayoutParams(0, dp(45), 1f))
        row1.addView(compact(s("▦ شبکه", "▦ Grid")) {
            showGrid = !showGrid
            overlay.showFrame = showGrid
        }, LinearLayout.LayoutParams(0, dp(45), 1f))
        row1.addView(compact(s("♙ ژست", "♙ Pose")) {
            overlay.template = if (overlay.template == null) selectedPose ?: PoseCatalog.all.first() else null
        }, LinearLayout.LayoutParams(0, dp(45), 1f))
        top.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row2.addView(compact(s("◎ ژست زنده", "◎ Live pose")) {
            livePoseOn = !livePoseOn
            if (!livePoseOn) overlay.setLive(null, 0, 0, 0, false)
            toast(s("تشخیص ژست زنده", "Live pose detection") + ": " + if (livePoseOn) s("روشن", "On") else s("خاموش", "Off"))
            bindCamera(preview)
        }, LinearLayout.LayoutParams(0, dp(45), 1f))
        row2.addView(compact(s("⏱ تایمر", "⏱ Timer")) {
            timerSec = when (timerSec) { 0 -> 3; 3 -> 10; else -> 0 }
            toast(s("تایمر", "Timer") + ": " + if (timerSec == 0) s("خاموش", "Off") else "$timerSec" + s(" ثانیه", " s"))
        }, LinearLayout.LayoutParams(0, dp(45), 1f))
        row2.addView(compact(s("— تراز", "— Level")) {
            levelOn = !levelOn
            if (levelOn) startTiltSensor(overlay) else { stopTiltSensor(); overlay.levelDegrees = null }
            toast(s("خط تراز", "Level line") + ": " + if (levelOn) s("روشن", "On") else s("خاموش", "Off"))
        }, LinearLayout.LayoutParams(0, dp(45), 1f))
        row2.addView(compact(s("🔦 چراغ", "🔦 Torch")) { toggleTorch() }, LinearLayout.LayoutParams(0, dp(45), 1f))
        top.addView(row2)
        content.addView(top, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Bottom panel.
        val bottom = vertical().apply { setPadding(dp(22), dp(10), dp(22), dp(18)); setBackgroundColor(Color.argb(165, 8, 12, 22)) }
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        modeRow.addView(compact(s("📷 عکس", "📷 Photo")) { if (!isRecording && videoMode) { videoMode = false; bindCamera(preview); refreshCameraTexts() } }, LinearLayout.LayoutParams(0, dp(42), 1f))
        modeRow.addView(compact(s("🎥 ویدیو", "🎥 Video")) { if (!isRecording && !videoMode) { videoMode = true; bindCamera(preview); refreshCameraTexts() } }, LinearLayout.LayoutParams(0, dp(42), 1f))
        bottom.addView(modeRow)
        val status = TextView(this).apply { gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 16f; text = "" }
        statusLabel = status
        bottom.addView(status)
        val zoomLabel = TextView(this).apply { text = s("زوم", "Zoom") + " 1.0×"; gravity = Gravity.CENTER; setTextColor(muted); textSize = 13f }
        bottom.addView(zoomLabel)
        val seek = SeekBar(this).apply { max = 100; progress = 0 }
        zoomSeek = seek
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val ratio = minZoom + (maxZoom - minZoom) * progress / 100f
                zoomLabel.text = s("زوم", "Zoom") + " " + "%.1f".format(Locale.US, ratio) + "×"
                if (fromUser) camera?.cameraControl?.setZoomRatio(ratio)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        bottom.addView(seek)
        val evRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        evRow.addView(TextView(this).apply { text = s("نوردهی", "Exposure"); setTextColor(muted); textSize = 13f }, LinearLayout.LayoutParams(-2, -2))
        val evSeek = SeekBar(this)
        evSeekBar = evSeek
        evSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val cam = camera ?: return
                val range = cam.cameraInfo.exposureState.exposureCompensationRange
                val index = range.lower + progress
                if (cam.cameraInfo.exposureState.isExposureCompensationSupported) cam.cameraControl.setExposureCompensationIndex(index)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        evRow.addView(evSeek, LinearLayout.LayoutParams(0, -2, 1f))
        evRow.tag = "evRow"
        bottom.addView(evRow)
        val shutter = action("") { onShutter(preview) }
        shutter.tag = "shutter"
        bottom.addView(shutter, LinearLayout.LayoutParams(-1, dp(57)))
        bottom.addView(note(s("برای فوکوس لمس کنید؛ برای زوم دو انگشت یا نوار زوم. عکس و ویدیو فقط روی گوشی ذخیره می‌شود.",
            "Tap to focus; pinch or use the slider to zoom. Photos and videos stay on this device.")))
        content.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        if (levelOn) startTiltSensor(overlay)
        refreshCameraTexts()
        bindCamera(preview)
    }

    private fun refreshCameraTexts() {
        val shutter = content.findViewWithTag<View>("shutter") as? Button
        shutter?.text = when {
            videoMode && isRecording -> "■ " + s("توقف ضبط", "Stop")
            videoMode -> "● " + s("شروع ضبط", "Record")
            else -> "● " + s("گرفتن عکس", "Take photo")
        }
        shutter?.setTextColor(if (videoMode) Color.WHITE else Color.rgb(3, 20, 21))
        val evRow = content.findViewWithTag<View>("evRow")
        evRow?.visibility = if (videoMode) View.GONE else View.VISIBLE
    }

    private fun setupEvSeek(evSeek: SeekBar) {
        val cam = camera ?: return
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) { evSeek.isEnabled = false; return }
        val range = state.exposureCompensationRange
        evSeek.max = range.upper - range.lower
        evSeek.progress = state.exposureCompensationIndex - range.lower
    }

    private fun syncZoomSeek(ratio: Float) {
        val seek = zoomSeek ?: return
        if (maxZoom <= minZoom) return
        seek.progress = ((ratio - minZoom) / (maxZoom - minZoom) * 100).toInt().coerceIn(0, 100)
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            toast(s("این لنز چراغ ندارد", "This lens has no light"))
            return
        }
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        toast(s("چراغ", "Torch") + ": " + if (torchOn) s("روشن", "On") else s("خاموش", "Off"))
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
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
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
                        .build()
                    imageCapture.flashMode = flashMode
                    capture = imageCapture
                    videoCapture = null
                    useCases.add(imageCapture)
                    if (livePoseOn) {
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
                        minZoom = state.minZoomRatio
                        maxZoom = state.maxZoomRatio
                        syncZoomSeek(state.zoomRatio)
                    }
                }
            } catch (e: Exception) {
                toast(s("راه‌اندازی دوربین ممکن نشد", "Camera failed to start") + ": ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

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
                    statusLabel?.text = ""
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
                    refreshCameraTexts()
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false
                    activeRecording = null
                    mainHandler.removeCallbacks(recordTicker)
                    statusLabel?.text = ""
                    statusLabel?.setTextColor(Color.WHITE)
                    refreshCameraTexts()
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

    // ------------------------------------------------------------------ poses

    private fun showPoses() {
        content.removeAllViews()
        val scroller = ScrollView(this)
        val list = vertical().apply { setPadding(dp(15), dp(15), dp(15), dp(16)) }
        list.addView(title(s("کتابخانه ژست‌های عکاسی", "Photo Pose Library")))
        list.addView(note(s(
            "${PoseCatalog.all.size} ژست ترسیمی در ${PoseCatalog.categories.size} دسته. یک ژست را انتخاب کنید تا روی دوربین نمایش داده شود؛ با «ژست زنده» اسکلت واقعی شما هم کنارش رسم می‌شود.",
            "${PoseCatalog.all.size} vector poses in ${PoseCatalog.categories.size} categories. Pick one to show it on the camera; with Live Pose your real skeleton is drawn next to it."
        )))
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val allChip = compact(s("همه", "All")) { poseFilterFa = null; showPoses() }
        chipRow.addView(allChip)
        for ((fa, en) in PoseCatalog.categories) {
            chipRow.addView(compact(I18n.s(fa, en)) { poseFilterFa = fa; showPoses() })
        }
        list.addView(HorizontalScrollView(this).apply { addView(chipRow) }, LinearLayout.LayoutParams(-1, dp(52)))
        val poses = if (poseFilterFa == null) PoseCatalog.all else PoseCatalog.all.filter { it.categoryFa == poseFilterFa }
        for (pose in poses) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = surface()
            }
            val sketch = PoseOverlayView(this).apply {
                template = pose
                showFrame = false
                setBackgroundColor(Color.rgb(14, 24, 35))
            }
            row.addView(sketch, LinearLayout.LayoutParams(dp(92), dp(132)))
            val info = vertical().apply { setPadding(dp(14), 0, dp(10), 0) }
            info.addView(label(pose.title(), 17f, Color.WHITE))
            info.addView(note(s("دسته", "Category") + ": " + pose.category()))
            info.addView(action(s("نمایش روی دوربین", "Show on camera")) {
                selectedPose = pose
                navigate(0)
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, dp(150)).apply { bottomMargin = dp(10) })
        }
        scroller.addView(list)
        content.addView(scroller, FrameLayout.LayoutParams(-1, -1))
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
        val layout = vertical().apply { setPadding(dp(12), dp(14), dp(12), dp(12)) }
        layout.addView(title(s("ویرایشگر عکس", "Photo Editor")))
        layout.addView(action(s("انتخاب عکس از گالری", "Pick a photo")) { imagePicker.launch("image/*") })
        val img = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(panel)
            setImageBitmap(editorBitmap)
        }
        editorImage = img
        layout.addView(img, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10); bottomMargin = dp(10) })

        val presetRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        for (preset in Effects.presets) {
            presetRow.addView(compact(preset.name()) {
                editorPreset = preset
                applyEditorFilter()
            })
        }
        layout.addView(HorizontalScrollView(this).apply { addView(presetRow) }, LinearLayout.LayoutParams(-1, dp(50)))

        val sliders = vertical()
        sliders.addView(sliderRow(s("شدت افکت", "Intensity"), 0, 100, (fxIntensity * 100).toInt()) { v -> fxIntensity = v / 100f; applyEditorFilter() })
        sliders.addView(sliderRow(s("روشنایی", "Brightness"), -60, 60, fxBrightness.toInt()) { v -> fxBrightness = v.toFloat(); applyEditorFilter() })
        sliders.addView(sliderRow(s("کنتراست", "Contrast"), 50, 150, (fxContrast * 100).toInt()) { v -> fxContrast = v / 100f; applyEditorFilter() })
        sliders.addView(sliderRow(s("اشباع رنگ", "Saturation"), 0, 200, (fxSaturation * 100).toInt()) { v -> fxSaturation = v / 100f; applyEditorFilter() })
        sliders.addView(sliderRow(s("گرمای رنگ", "Warmth"), -50, 50, fxWarmth.toInt()) { v -> fxWarmth = v.toFloat(); applyEditorFilter() })
        val scroller = ScrollView(this).apply { addView(sliders) }
        layout.addView(scroller, LinearLayout.LayoutParams(-1, dp(168)))

        val editRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        editRow.addView(compact(s("⟳ چرخش", "⟳ Rotate")) { transformBitmap(rotate = true) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        editRow.addView(compact(s("⇋ برگردان", "⇋ Flip")) { transformBitmap(rotate = false) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        editRow.addView(compact(s("↺ بازنشانی", "↺ Reset")) { resetEffects(); showEditor() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        layout.addView(editRow)
        layout.addView(action(s("ذخیره نسخه ویرایش‌شده", "Save edited copy")) { saveEditedCopy() })
        layout.addView(note(s("فایل اصلی بدون تغییر باقی می‌ماند. پردازش آنلاین AI هنوز به سرور متصل نشده است.",
            "The original file is never changed. Online AI processing is not connected yet.")))
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

    // ------------------------------------------------------------------ settings

    private fun showSettings() {
        content.removeAllViews()
        val scroller = ScrollView(this)
        val layout = vertical().apply { setPadding(dp(20), dp(18), dp(20), dp(18)) }

        layout.addView(title("Zoom AI Studio | 0.2.0"))
        layout.addView(note(s("استودیوی دوربین محلی — بدون سرور، بدون آپلود عکس", "Local camera studio — no server, no photo uploads")))

        layout.addView(title(s("زبان", "Language")))
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        langRow.addView(compact("فارسی") { setLanguage(Lang.FA) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        langRow.addView(compact("English") { setLanguage(Lang.EN) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        layout.addView(langRow)
        layout.addView(note(s("زبان فعلی: فارسی", "Current language: English")))

        layout.addView(title(s("تنظیمات دوربین", "Camera defaults")))
        val gridRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        gridRow.addView(compact(s("شبکه روشن", "Grid on")) {
            prefs.edit().putBoolean("grid_default", true).apply()
            showGrid = true
            toast(s("شبکه به‌صورت پیش‌فرض روشن شد", "Grid will be on by default"))
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        gridRow.addView(compact(s("شبکه خاموش", "Grid off")) {
            prefs.edit().putBoolean("grid_default", false).apply()
            showGrid = false
            toast(s("شبکه به‌صورت پیش‌فرض خاموش شد", "Grid will be off by default"))
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        layout.addView(gridRow)
        layout.addView(note(s("عکس‌ها در گالری ▸ Pictures/ZoomAIStudio و ویدیوها در Movies/ZoomAIStudio ذخیره می‌شوند.",
            "Photos are saved to Gallery ▸ Pictures/ZoomAIStudio and videos to Movies/ZoomAIStudio.")))

        layout.addView(title(s("سازنده و کارهای دیگر من", "Developer & my other work")))
        layout.addView(label("Noorullah Haidari", 17f, Color.WHITE))
        layout.addView(note(s("توسعه‌دهنده فول‌استک وب و موبایل — کابل، افغانستان",
            "Full-stack web & mobile developer — Kabul, Afghanistan")))
        layout.addView(action(s("🌐 وب‌سایت من — noorcode.tech", "🌐 My website — noorcode.tech")) { openLink("https://noorcode.tech") })
        val linkRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        linkRow1.addView(compact("GitHub") { openLink("https://github.com/haidarzada987-tech") }, LinearLayout.LayoutParams(0, dp(48), 1f))
        linkRow1.addView(compact("LinkedIn") { openLink("https://www.linkedin.com/in/noorullah-haidari-44863142b") }, LinearLayout.LayoutParams(0, dp(48), 1f))
        layout.addView(linkRow1)
        val linkRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        linkRow2.addView(compact("Instagram") { openLink("https://www.instagram.com/noo_rullha") }, LinearLayout.LayoutParams(0, dp(48), 1f))
        linkRow2.addView(compact("WhatsApp") { openLink("https://wa.me/93748767632") }, LinearLayout.LayoutParams(0, dp(48), 1f))
        layout.addView(linkRow2)
        layout.addView(action(s("✉️ ایمیل — haidarzada987@gmail.com", "✉️ Email — haidarzada987@gmail.com")) {
            openLink("mailto:haidarzada987@gmail.com")
        })
        layout.addView(note(s("اپ‌های دیگر من:", "My other apps & projects:")))
        layout.addView(action(s("📱 BMI Pro — اپ سلامت و شاخص توده بدنی", "📱 BMI Pro — BMI & wellness app")) {
            openLink("https://github.com/haidarzada987-tech/BMI-Pro")
        })
        layout.addView(action(s("🛒 Toba Studio — پلتفرم خدمات دیجیتال", "🛒 Toba Studio — digital services platform")) {
            openLink("https://github.com/haidarzada987-tech/Toba-Studio")
        })
        layout.addView(note(s("برای سفارش وب‌سایت یا اپلیکیشن، از واتساپ یا ایمیل در تماس شوید.",
            "For website or app orders, reach me on WhatsApp or email.")))

        layout.addView(title(s("امکانات فعال", "Active features")))
        layout.addView(note(s(
            "• عکس و ویدیو با CameraX، زوم دو انگشتی و نواری تا حد سخت‌افزار گوشی، فوکوس لمسی با حلقه نمایش\n• تشخیص ژست زنده روی دستگاه (ML Kit) + ۷۳ ژست راهنمای ترسیمی در ۱۲ دسته\n• جبران نوردهی، تایمر ۳ و ۱۰ ثانیه، خط تراز افق، فلش و چراغ\n• ویرایشگر: ۲۴ افکت، شدت و تنظیم روشنایی/کنتراست/اشباع/گرما، چرخش و برگردان\n• فارسی و انگلیسی، کاملاً محلی و بدون آپلود خودکار",
            "• Photo & video with CameraX, pinch + slider zoom up to the hardware limit, tap-to-focus with a focus ring\n• On-device live pose detection (ML Kit) + 73 vector pose guides in 12 categories\n• Exposure compensation, 3s/10s timer, horizon level line, flash and torch\n• Editor: 24 effects, intensity plus brightness/contrast/saturation/warmth, rotate and flip\n• Persian & English UI, fully local, no automatic uploads"
        )))
        layout.addView(title(s("بخش‌های برنامه‌ریزی‌شده", "Planned")))
        layout.addView(note(s(
            "• هوش مصنوعی آنلاین برای ارتقای تصویر و حذف اشیاء (فقط با رضایت صریح و سرور امن)\n• عکس‌های مرجع دارای مجوز برای ژست‌ها\n• حالت شب و HDR وابسته به سخت‌افزار",
            "• Online AI for image enhancement and object removal (only with explicit consent and a secure server)\n• Licensed photo references for poses\n• Hardware-dependent night mode and HDR"
        )))
        layout.addView(title(s("حریم خصوصی", "Privacy")))
        layout.addView(note(s(
            "همه عکس‌ها و ویدیوها روی گوشی شما می‌مانند. تشخیص ژست روی خود دستگاه انجام می‌شود و هیچ تصویری به اینترنت فرستاده نمی‌شود.",
            "All photos and videos stay on your phone. Pose detection runs on the device itself and no image is sent to the internet."
        )))
        scroller.addView(layout)
        content.addView(scroller, FrameLayout.LayoutParams(-1, -1))
    }

    private fun setLanguage(lang: Lang) {
        if (I18n.lang == lang) return
        prefs.edit().putString("lang", if (lang == Lang.EN) "en" else "fa").apply()
        I18n.lang = lang
        recreate()
    }

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast(s("باز کردن لینک ممکن نشد", "Could not open the link"))
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
    private fun title(value: String) = label(value, 20f, Color.WHITE).apply { setPadding(0, dp(12), 0, dp(12)) }
    private fun note(value: String) = label(value, 13f, muted).apply { setLineSpacing(dp(3).toFloat(), 1f) }
    private fun surface() = GradientDrawable().apply { setColor(panel); cornerRadius = dp(14).toFloat() }
    private fun action(value: String, task: () -> Unit) = Button(this).apply {
        text = value; textSize = 15f; isAllCaps = false
        setTextColor(Color.rgb(3, 20, 21))
        background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(13).toFloat() }
        setOnClickListener { task() }
    }
    private fun compact(value: String, task: () -> Unit) = Button(this).apply {
        text = value; textSize = 11f; isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(Color.argb(205, 27, 41, 55)); cornerRadius = dp(10).toFloat() }
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
