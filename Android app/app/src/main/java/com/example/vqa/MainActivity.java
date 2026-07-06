package com.example.vqa;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.view.ScaleGestureDetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private BottomNavigationView bottomNavigationView;
    private Button btnSubmit;
    private EditText etQuestion;
    private ImageButton btnMic, btnTts;
    private ProgressBar progressBar;
    private TextView tvAnswer;

    private TextToSpeech textToSpeech;
    private SpeechRecognizer continuousRecognizer;
    
    private String lastAnswer = "";
    private String sessionId = UUID.randomUUID().toString();
    private String currentMode = "VQA"; // VQA, OCR, NAV

    private static final int SPEECH_REQUEST_CODE = 102;
    private static final int PERMISSION_REQUEST_CODE = 103;

    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Handler navHandler = new Handler(Looper.getMainLooper());
    private boolean isNavigating = false;

    private static final String BASE_URL = "https://avinavpri-vqa-backend.hf.space";
    
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFinder = findViewById(R.id.viewFinder);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        btnSubmit = findViewById(R.id.btnSubmit);
        etQuestion = findViewById(R.id.etQuestion);
        btnMic = findViewById(R.id.btnMic);
        btnTts = findViewById(R.id.btnTts);
        progressBar = findViewById(R.id.progressBar);
        tvAnswer = findViewById(R.id.tvAnswer);

        cameraExecutor = Executors.newSingleThreadExecutor();

        checkPermissions();

        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_vision) {
                setMode("VQA");
                return true;
            } else if (id == R.id.nav_ocr) {
                setMode("OCR");
                return true;
            } else if (id == R.id.nav_navigation) {
                setMode("NAV");
                return true;
            }
            return false;
        });

        btnMic.setOnClickListener(v -> startSpeechRecognition());
        
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
        
        btnTts.setOnClickListener(v -> {
            if (!lastAnswer.isEmpty()) {
                speak(lastAnswer);
            }
        });

        btnSubmit.setOnClickListener(v -> {
            if (currentMode.equals("NAV")) {
                toggleNavigation();
            } else {
                captureAndProcess();
            }
        });
        
        // Setup wake word listener
        setupContinuousSpeechRecognition();
    }

    private void setMode(String mode) {
        currentMode = mode;
        if (isNavigating) toggleNavigation();

        btnMic.setVisibility(View.VISIBLE);

        if (mode.equals("VQA")) {
            etQuestion.setVisibility(View.VISIBLE);
            btnSubmit.setText("Ask AI");
        } else if (mode.equals("OCR")) {
            etQuestion.setVisibility(View.GONE);
            btnSubmit.setText("Read Text");
        } else if (mode.equals("NAV")) {
            etQuestion.setVisibility(View.GONE);
            btnSubmit.setText("Start Nav");
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                setupContinuousSpeechRecognition();
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

                ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(this,
                        new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(ScaleGestureDetector detector) {
                                float currentZoom = camera.getCameraInfo().getZoomState().getValue().getZoomRatio();
                                float newZoom = currentZoom * detector.getScaleFactor();
                                camera.getCameraControl().setZoomRatio(newZoom);
                                return true;
                            }
                        });

                viewFinder.setOnTouchListener((v, event) -> {
                    scaleGestureDetector.onTouchEvent(event);
                    return true;
                });
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupContinuousSpeechRecognition() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            continuousRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            continuousRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {}
                @Override
                public void onBeginningOfSpeech() {}
                @Override
                public void onRmsChanged(float rmsdB) {}
                @Override
                public void onBufferReceived(byte[] buffer) {}
                @Override
                public void onEndOfSpeech() {}
                @Override
                public void onError(int error) {
                    // Restart listening on error (e.g. timeout)
                    listenForWakeWord();
                }
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null) {
                        for (String result : matches) {
                            String lower = result.toLowerCase();
                            if (lower.contains("hey vision")) {
                                speak("Listening...");
                                // Extract query after "hey vision" if it exists
                                int idx = lower.indexOf("hey vision");
                                String query = lower.substring(idx + 10).trim();
                                if (!query.isEmpty()) {
                                    processVoiceIntent(query);
                                } else {
                                    // Start actual prompt
                                    startSpeechRecognition();
                                }
                                break;
                            }
                        }
                    }
                    listenForWakeWord();
                }
                @Override
                public void onPartialResults(Bundle partialResults) {}
                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
            listenForWakeWord();
        }
    }
    
    private void listenForWakeWord() {
        if (continuousRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            continuousRecognizer.startListening(intent);
        }
    }

    private void startSpeechRecognition() {
        if (continuousRecognizer != null) {
            continuousRecognizer.cancel();
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about what you see...");
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == SPEECH_REQUEST_CODE && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                processVoiceIntent(matches.get(0));
            }
        }
        listenForWakeWord(); // Resume background listening
    }

    private void processVoiceIntent(String query) {
        String lower = query.toLowerCase();
        etQuestion.setText(query);

        if (lower.contains("read") || lower.contains("text") || lower.contains("sign") || lower.contains("document") || lower.contains("ocr")) {
            setMode("OCR");
            bottomNavigationView.setSelectedItemId(R.id.nav_ocr);
            captureAndProcess();
        } else if (lower.contains("navigate") || lower.contains("guide") || lower.contains("walk") || lower.contains("path") || lower.contains("direction")) {
            setMode("NAV");
            bottomNavigationView.setSelectedItemId(R.id.nav_navigation);
            if (!isNavigating) {
                toggleNavigation();
            }
        } else if (lower.contains("stop navigation") || lower.contains("stop guide")) {
            if (isNavigating) {
                toggleNavigation();
            }
        } else {
            setMode("VQA");
            bottomNavigationView.setSelectedItemId(R.id.nav_vision);
            captureAndProcess();
        }
    }

    private void toggleNavigation() {
        isNavigating = !isNavigating;
        if (isNavigating) {
            btnSubmit.setText("Stop Nav");
            navHandler.post(navigationRunnable);
            speak("Navigation started");
        } else {
            btnSubmit.setText("Start Nav");
            navHandler.removeCallbacks(navigationRunnable);
            speak("Navigation stopped");
        }
    }

    private boolean isProcessingFrame = false;

    private Runnable navigationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isNavigating) return;
            if (!isProcessingFrame) {
                isProcessingFrame = true;
                captureAndProcess();
            }
            navHandler.postDelayed(this, 2000); // Process every 2 seconds
        }
    };

    private void captureAndProcess() {
        if (imageCapture == null) return;

        if (!currentMode.equals("NAV")) {
            progressBar.setVisibility(View.VISIBLE);
            tvAnswer.setVisibility(View.GONE);
            btnSubmit.setEnabled(false);
        }

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                java.nio.ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                image.close();
                processImage(bytes);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                isProcessingFrame = false;
                runOnUiThread(() -> {
                    if (!currentMode.equals("NAV")) {
                        progressBar.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        Toast.makeText(MainActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void processImage(byte[] imageBytes) {
        String url = BASE_URL;
        String question = etQuestion.getText().toString().trim();
        
        MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("image", "capture.jpg", RequestBody.create(imageBytes, MediaType.parse("image/jpeg")));

        if (currentMode.equals("VQA")) {
            if (question.isEmpty()) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Please ask a question", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            url += "/predict";
            builder.addFormDataPart("question", question);
            builder.addFormDataPart("session_id", sessionId);
        } else if (currentMode.equals("OCR")) {
            url += "/ocr";
        } else if (currentMode.equals("NAV")) {
            url += "/navigate";
        }

        RequestBody requestBody = builder.build();
        Request request = new Request.Builder().url(url).post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                isProcessingFrame = false;
                runOnUiThread(() -> {
                    if (!currentMode.equals("NAV")) {
                        progressBar.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                        tvAnswer.setText("Error: " + e.getMessage());
                        tvAnswer.setVisibility(View.VISIBLE);
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                isProcessingFrame = false;
                String respStr = response.body().string();
                runOnUiThread(() -> {
                    if (!currentMode.equals("NAV")) {
                        progressBar.setVisibility(View.GONE);
                        btnSubmit.setEnabled(true);
                    }
                    
                    try {
                        if (response.isSuccessful()) {
                            JSONObject json = new JSONObject(respStr);
                            String answerText = "";
                            
                            if (currentMode.equals("VQA")) {
                                answerText = json.getJSONArray("predictions").getJSONObject(0).getString("answer");
                            } else if (currentMode.equals("OCR")) {
                                answerText = json.getString("text");
                                if (answerText.trim().isEmpty()) answerText = "No text found.";
                            } else if (currentMode.equals("NAV")) {
                                JSONArray detections = json.getJSONArray("detections");
                                if (detections.length() > 0) {
                                    answerText = detections.getJSONObject(0).getString("object") + " ahead";
                                }
                            }

                            if (!answerText.isEmpty()) {
                                lastAnswer = answerText;
                                if (!currentMode.equals("NAV")) {
                                    tvAnswer.setText(answerText);
                                    tvAnswer.setVisibility(View.VISIBLE);
                                    btnTts.setVisibility(View.VISIBLE);
                                }
                                speak(answerText);
                            }
                        } else {
                            if (!currentMode.equals("NAV")) {
                                tvAnswer.setText("Server Error: " + response.code());
                                tvAnswer.setVisibility(View.VISIBLE);
                            }
                        }
                    } catch (JSONException e) {
                        if (!currentMode.equals("NAV")) {
                            tvAnswer.setText("Failed to parse response");
                            tvAnswer.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        });
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (continuousRecognizer != null) {
            continuousRecognizer.destroy();
        }
        navHandler.removeCallbacks(navigationRunnable);
        cameraExecutor.shutdown();
        super.onDestroy();
    }
}