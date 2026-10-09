package com.example.rawcamera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

data class CameraSettings(
    val iso: Int = 100,
    val exposureTimeNs: Long = 10_000_000L,
    val focusDistance: Float = 0f,
    val whiteBalance: Int = 5000,
    val lensFacing: Int = CameraCharacteristics.LENS_FACING_BACK,
    val zoomRatio: Float = 1.0f,
    val captureRaw: Boolean = true,
    val jpegQuality: Int = 100
)

data class LensInfo(
    val id: String,
    val facing: Int,
    val focalLength: Float,
    val hardwareLevel: Int,
    val maxZoom: Float,
    val isRawCapable: Boolean
)

class CameraController(private val context: Context) {
    private val tag = "CameraController"
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val cameraOpenLock = Semaphore(1)

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    var onJpegCaptured: ((String) -> Unit)? = null
    var onRawCaptured: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val availableLenses: List<LensInfo> by lazy { discoverLenses() }

    @SuppressLint("MissingPermission")
    fun open(cameraId: String, previewSurface: Surface, settings: CameraSettings) {
        if (!cameraOpenLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
            onError?.invoke("Nie można otworzyć kamery (timeout)")
            return
        }
        startBackgroundThread()

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw IllegalStateException("Brak mapy konfiguracji")

        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
        val rawSizes = map.getOutputSizes(ImageFormat.RAW_SENSOR)

        val previewSize = chooseOptimalSize(
            map.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray(),
            1920, 1080
        )
        val jpegSize = chooseOptimalSize(jpegSizes, 4000, 3000)

        jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let {
                    val path = JpegSaver.save(context, it, settings.jpegQuality)
                    it.close()
                    onJpegCaptured?.invoke(path)
                }
            }, backgroundHandler)
        }

        if (settings.captureRaw && rawSizes != null && rawSizes.isNotEmpty()) {
            val rawSize = chooseOptimalSize(rawSizes, 4000, 3000)
            rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                setOnImageAvailableListener({ reader ->
                    val image = reader.acquireLatestImage()
                    image?.let {
                        val path = RawSaver.save(context, it)
                        it.close()
                        onRawCaptured?.invoke(path)
                    }
                }, backgroundHandler)
            }
        }

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraOpenLock.release()
                cameraDevice = camera
                createSession(camera, previewSurface, settings)
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraOpenLock.release()
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                cameraOpenLock.release()
                camera.close()
                cameraDevice = null
                onError?.invoke("Błąd kamery: $error")
            }
        }, backgroundHandler)
    }

    private fun createSession(camera: CameraDevice, previewSurface: Surface, settings: CameraSettings) {
        val surfaces = mutableListOf(previewSurface, jpegReader!!.surface)
        rawReader?.let { surfaces.add(it.surface) }

        camera.createCaptureSession(
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                surfaces.map { OutputConfiguration(it) },
                { it.run() },
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        startPreview(session, previewSurface, settings)
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError?.invoke("Konfiguracja sesji nieudana")
                    }
                }
            )
        )
    }

    private fun startPreview(session: CameraCaptureSession, previewSurface: Surface, settings: CameraSettings) {
        val request = buildRequest(previewSurface, settings, TemplateType.PREVIEW)
        session.setRepeatingRequest(request, null, backgroundHandler)
    }

    fun capture(settings: CameraSettings) {
        val session = captureSession ?: return
        val device = cameraDevice ?: return

        val surfaces = mutableListOf(jpegReader!!.surface)
        rawReader?.let { surfaces.add(it.surface) }

        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL).apply {
            surfaces.forEach { addTarget(it) }
            applyManualSettings(this, settings)
        }
        session.capture(builder.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                Log.d(tag, "Capture completed")
            }
        }, backgroundHandler)
    }

    fun applyPreviewSettings(settings: CameraSettings, previewSurface: Surface) {
        val session = captureSession ?: return
        val request = buildRequest(previewSurface, settings, TemplateType.PREVIEW)
        session.setRepeatingRequest(request, null, backgroundHandler)
    }

    private enum class TemplateType { PREVIEW, CAPTURE }

    private fun buildRequest(surface: Surface, settings: CameraSettings, type: TemplateType): CaptureRequest {
        val device = cameraDevice!!
        val template = when (type) {
            TemplateType.PREVIEW -> CameraDevice.TEMPLATE_PREVIEW
            TemplateType.CAPTURE -> CameraDevice.TEMPLATE_MANUAL
        }
        val builder = device.createCaptureRequest(template)
        builder.addTarget(surface)
        applyManualSettings(builder, settings)
        return builder.build()
    }

    private fun applyManualSettings(builder: CaptureRequest.Builder, settings: CameraSettings) {
        builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
        builder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_OFF)
        builder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF)
        builder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_OFF)
        builder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_OFF)

        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
        builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)

        builder.set(CaptureRequest.SENSOR_SENSITIVITY, settings.iso)
        builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, settings.exposureTimeNs)
        builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, settings.focusDistance)
        builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
        builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, kelvinToGains(settings.whiteBalance))
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, settings.zoomRatio.coerceIn(1f, 10f))
    }

    private fun kelvinToGains(kelvin: Int): android.hardware.camera2.params.RggbChannelVector {
        val norm = (kelvin - 2000f) / 6000f
        val r = 1.5f - norm * 0.5f
        val g = 1.0f
        val b = 0.5f + norm * 0.5f
        return android.hardware.camera2.params.RggbChannelVector(r, g, g, b)
    }

    private fun discoverLenses(): List<LensInfo> {
        val result = mutableListOf<LensInfo>()
        for (id in cameraManager.cameraIdList) {
            val c = cameraManager.getCameraCharacteristics(id)
            val facing = c.get(CameraCharacteristics.LENS_FACING) ?: continue
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
            val level = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
            val maxZoom = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.upper?.toFloat() ?: (c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
            val rawCapable = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) ?: false
            result.add(LensInfo(id, facing, focal, level, maxZoom, rawCapable))
        }
        return result
    }

    fun getZoomRange(cameraId: String): ClosedFloatingPointRange<Float> {
        val c = cameraManager.getCameraCharacteristics(cameraId)
        val range = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        return if (range != null) range.lower.toFloat()..range.upper.toFloat()
        else 1f..(c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f)
    }

    fun close() {
        try { cameraOpenLock.tryAcquire(2500, TimeUnit.MILLISECONDS) } catch (_: Exception) {}
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        jpegReader?.close()
        jpegReader = null
        rawReader?.close()
        rawReader = null
        cameraOpenLock.release()
        stopBackgroundThread()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try { backgroundThread?.join(); backgroundThread = null } catch (_: Exception) {}
    }

    private fun chooseOptimalSize(sizes: Array<Size>, targetW: Int, targetH: Int): Size {
        if (sizes.isEmpty()) return Size(1920, 1080)
        val targetArea = targetW.toLong() * targetH
        return sizes.minByOrNull { kotlin.math.abs(it.width.toLong() * it.height - targetArea) } ?: sizes[0]
    }
}
