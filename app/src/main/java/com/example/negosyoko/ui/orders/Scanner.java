package com.example.negosyoko.ui.orders;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.cardview.widget.CardView;

import com.example.negosyoko.R;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Scanner extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String TAG = "Scanner";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 101;

    // UI Components
    private SurfaceView cameraPreview;
    private SurfaceHolder surfaceHolder;
    private ImageButton btnBack, btnFlash, btnGallery, btnCapture, btnHistory;
    private ImageButton btnCloseResult, btnCancelManual, btnConfirmManual;
    private MaterialButton btnManualEntry, btnAddToOrder, btnScanAgain;
    private TextView tvScanStatus, tvResultTitle, tvResultMessage;
    private CardView cardScanResult;
    private LinearLayout layoutManualEntry;
    private TextInputEditText etManualCode;
    private View scanningLine;
    private ImageView ivResultIcon;

    // Camera and Detection
    private CameraSource cameraSource;
    private BarcodeDetector barcodeDetector;
    private boolean isFlashOn = false;
    private boolean isScanning = true;
    private ObjectAnimator scanLineAnimator;

    // Scanning state
    private String lastScannedCode = "";
    private long lastScanTime = 0;
    private static final long SCAN_DEBOUNCE_TIME = 2000; // 2 seconds

    // Scanner callback interface
    public interface ScannerCallback {
        void onScanSuccess(String code, String format);
        void onScanError(String error);
        void onManualEntry(String code);
    }

    private ScannerCallback scannerCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scanner);

        initializeViews();
        setupClickListeners();
        setupBarcodeDetector();
        setupScanningAnimation();

        // Set initial icon states
        btnFlash.setImageResource(R.drawable.flash_off_icon);
        btnBack.setImageResource(R.drawable.back_icon);

        // Check camera permission
        if (checkCameraPermission()) {
            initializeCamera();
        } else {
            requestCameraPermission();
        }
    }

    private void initializeViews() {
        cameraPreview = findViewById(R.id.camera_preview);
        btnBack = findViewById(R.id.back);
        btnFlash = findViewById(R.id.btn_flash);
        btnCapture = findViewById(R.id.btn_capture);
        btnManualEntry = findViewById(R.id.btn_manual_entry);
        btnAddToOrder = findViewById(R.id.btn_add_to_order);
        btnScanAgain = findViewById(R.id.btn_scan_again);
        btnCancelManual = findViewById(R.id.btn_cancel_manual);
        btnConfirmManual = findViewById(R.id.btn_confirm_manual);

        tvScanStatus = findViewById(R.id.tv_scan_status);
        tvResultTitle = findViewById(R.id.tv_result_title);
        tvResultMessage = findViewById(R.id.tv_result_message);

        cardScanResult = findViewById(R.id.card_scan_result);
        layoutManualEntry = findViewById(R.id.layout_manual_entry);
        etManualCode = findViewById(R.id.et_manual_code);
        scanningLine = findViewById(R.id.scanning_line);

        surfaceHolder = cameraPreview.getHolder();
        surfaceHolder.addCallback(this);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnFlash.setOnClickListener(v -> toggleFlash());

        btnGallery.setOnClickListener(v -> openGallery());

        btnCapture.setOnClickListener(v -> captureImage());

        btnHistory.setOnClickListener(v -> openScanHistory());

        btnManualEntry.setOnClickListener(v -> showManualEntry());

        btnAddToOrder.setOnClickListener(v -> addToOrder());

        btnScanAgain.setOnClickListener(v -> startScanning());

        btnCloseResult.setOnClickListener(v -> hideResult());

        btnCancelManual.setOnClickListener(v -> hideManualEntry());

        btnConfirmManual.setOnClickListener(v -> confirmManualEntry());
    }

    private void setupBarcodeDetector() {
        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.ALL_FORMATS)
                .build();

        if (!barcodeDetector.isOperational()) {
            Log.w(TAG, "Detector dependencies are not yet available.");
            Toast.makeText(this, "Barcode detector is not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {
            @Override
            public void release() {
                // Cleanup
            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {
                if (!isScanning) return;

                SparseArray<Barcode> barcodes = detections.getDetectedItems();
                if (barcodes.size() > 0) {
                    // Get the first detected barcode
                    Barcode barcode = barcodes.valueAt(0);
                    handleBarcodeDetection(barcode);
                }
            }
        });
    }

    private void setupScanningAnimation() {
        scanLineAnimator = ObjectAnimator.ofFloat(scanningLine, "translationY", -100f, 100f);
        scanLineAnimator.setDuration(2000);
        scanLineAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scanLineAnimator.setRepeatMode(ValueAnimator.REVERSE);
        scanLineAnimator.setInterpolator(new LinearInterpolator());
        scanLineAnimator.start();
    }

    private void handleBarcodeDetection(Barcode barcode) {
        long currentTime = System.currentTimeMillis();
        String code = barcode.displayValue;

        // Debounce repeated scans
        if (code.equals(lastScannedCode) &&
                (currentTime - lastScanTime) < SCAN_DEBOUNCE_TIME) {
            return;
        }

        lastScannedCode = code;
        lastScanTime = currentTime;

        runOnUiThread(() -> {
            isScanning = false;
            stopScanningAnimation();
            showScanResult(code, getBarcodeFormat(barcode.format));

            if (scannerCallback != null) {
                scannerCallback.onScanSuccess(code, getBarcodeFormat(barcode.format));
            }
        });
    }

    private String getBarcodeFormat(int format) {
        switch (format) {
            case Barcode.CODE_128: return "CODE_128";
            case Barcode.CODE_39: return "CODE_39";
            case Barcode.CODE_93: return "CODE_93";
            case Barcode.CODABAR: return "CODABAR";
            case Barcode.DATA_MATRIX: return "DATA_MATRIX";
            case Barcode.EAN_13: return "EAN_13";
            case Barcode.EAN_8: return "EAN_8";
            case Barcode.ITF: return "ITF";
            case Barcode.QR_CODE: return "QR_CODE";
            case Barcode.UPC_A: return "UPC_A";
            case Barcode.UPC_E: return "UPC_E";
            case Barcode.PDF417: return "PDF417";
            case Barcode.AZTEC: return "AZTEC";
            default: return "UNKNOWN";
        }
    }

    private void initializeCamera() {
        if (cameraSource != null) {
            cameraSource.release();
        }

        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setFacing(CameraSource.CAMERA_FACING_BACK)
                .setRequestedPreviewSize(1920, 1080)
                .setRequestedFps(30.0f)
                .setAutoFocusEnabled(true)
                .build();

        startCameraPreview();
    }

    private void startCameraPreview() {
        try {
            if (checkCameraPermission()) {
                cameraSource.start(surfaceHolder);
                updateScanStatus("Looking for barcode...");
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to start camera source.", e);
            cameraSource.release();
            cameraSource = null;
        }
    }

    private void toggleFlash() {
        if (cameraSource != null) {
            isFlashOn = !isFlashOn;
            try {
                // Get the underlying camera using reflection
                Field cameraField = CameraSource.class.getDeclaredField("mCamera");
                cameraField.setAccessible(true);
                Camera camera = (Camera) cameraField.get(cameraSource);

                if (camera != null) {
                    Camera.Parameters params = camera.getParameters();
                    if (isFlashOn) {
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        btnFlash.setImageResource(R.drawable.flash_on_icon);
                    } else {
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        btnFlash.setImageResource(R.drawable.flash_off_icon);
                    }
                    camera.setParameters(params);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error toggling flash", e);
                Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show();
                // Reset state if failed
                isFlashOn = !isFlashOn;
            }
        }
    }

    private void captureImage() {
        updateScanStatus("Capturing...");
        Toast.makeText(this, "Image captured", Toast.LENGTH_SHORT).show();
    }

    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
    }

    private void openScanHistory() {
        Toast.makeText(this, "Scan history feature", Toast.LENGTH_SHORT).show();
    }

    private void showManualEntry() {
        layoutManualEntry.setVisibility(View.VISIBLE);
        etManualCode.requestFocus();
    }

    private void hideManualEntry() {
        layoutManualEntry.setVisibility(View.GONE);
        etManualCode.setText("");
    }

    private void confirmManualEntry() {
        String code = etManualCode.getText().toString().trim();
        if (code.isEmpty()) {
            etManualCode.setError("Please enter a valid code");
            return;
        }

        hideManualEntry();
        showScanResult(code, "MANUAL_ENTRY");

        if (scannerCallback != null) {
            scannerCallback.onManualEntry(code);
        }
    }

    private void showScanResult(String code, String format) {
        cardScanResult.setVisibility(View.VISIBLE);
        tvResultTitle.setText("Product Found!");
        tvResultMessage.setText("Scanned: " + code + " (" + format + ")");

        stopScanningAnimation();
        updateScanStatus("Scan complete");
    }

    private void hideResult() {
        cardScanResult.setVisibility(View.GONE);
        startScanning();
    }

    private void addToOrder() {
        Toast.makeText(this, "Added to order: " + lastScannedCode, Toast.LENGTH_SHORT).show();
        hideResult();
    }

    private void startScanning() {
        isScanning = true;
        cardScanResult.setVisibility(View.GONE);
        updateScanStatus("Looking for barcode...");
        startScanningAnimation();
    }

    private void startScanningAnimation() {
        if (scanLineAnimator != null && !scanLineAnimator.isRunning()) {
            scanLineAnimator.start();
        }
    }

    private void stopScanningAnimation() {
        if (scanLineAnimator != null && scanLineAnimator.isRunning()) {
            scanLineAnimator.cancel();
        }
    }

    private void updateScanStatus(String status) {
        tvScanStatus.setText(status);
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            } else {
                Toast.makeText(this, "Camera permission is required for scanning",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            Toast.makeText(this, "Gallery image selected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (cameraSource != null) {
            startCameraPreview();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // Handle surface changes if needed
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (cameraSource != null) {
            cameraSource.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraSource != null && surfaceHolder != null) {
            startCameraPreview();
            startScanning();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraSource != null) {
            cameraSource.stop();
        }
        stopScanningAnimation();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraSource != null) {
            cameraSource.release();
            cameraSource = null;
        }
        if (barcodeDetector != null) {
            barcodeDetector.release();
        }
        if (scanLineAnimator != null) {
            scanLineAnimator.cancel();
        }
    }

    public void setScannerCallback(ScannerCallback callback) {
        this.scannerCallback = callback;
    }

    public void pauseScanning() {
        isScanning = false;
        stopScanningAnimation();
    }

    public void resumeScanning() {
        startScanning();
    }

    public String getLastScannedCode() {
        return lastScannedCode;
    }

    public boolean isCurrentlyScanning() {
        return isScanning;
    }
}