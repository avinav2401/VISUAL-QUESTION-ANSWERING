package com.example.vqa;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
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

    private ImageView ivPreview;
    private TextView tvDropText, tvFormats, tvAnswer;
    private Button btnBrowse, btnCamera, btnSubmit;
    private EditText etQuestion;
    private ImageButton btnMic, btnTts;
    private ProgressBar progressBar;
    private TextView chip1, chip2, chip3;

    private TextToSpeech textToSpeech;
    private String lastAnswer = "";

    private static final int CAMERA_REQUEST_CODE = 100;
    private static final int GALLERY_REQUEST_CODE = 101;
    private static final int SPEECH_REQUEST_CODE = 102;
    private static final int PERMISSION_REQUEST_CODE = 103;

    private String currentPhotoPath;
    private Uri currentImageUri;
    private Bitmap currentBitmap;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ivPreview = findViewById(R.id.ivPreview);
        tvDropText = findViewById(R.id.tvDropText);
        tvFormats = findViewById(R.id.tvFormats);
        tvAnswer = findViewById(R.id.tvAnswer);
        btnBrowse = findViewById(R.id.btnBrowse);
        btnCamera = findViewById(R.id.btnCamera);
        btnSubmit = findViewById(R.id.btnSubmit);
        etQuestion = findViewById(R.id.etQuestion);
        btnMic = findViewById(R.id.btnMic);
        progressBar = findViewById(R.id.progressBar);

        chip1 = findViewById(R.id.chip1);
        chip2 = findViewById(R.id.chip2);
        chip3 = findViewById(R.id.chip3);

        checkPermissions();

        btnBrowse.setOnClickListener(v -> openGallery());
        btnCamera.setOnClickListener(v -> openCamera());
        btnMic.setOnClickListener(v -> startSpeechRecognition());
        
        btnTts = findViewById(R.id.btnTts);
        textToSpeech = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
        btnTts.setOnClickListener(v -> {
            if (!lastAnswer.isEmpty()) {
                textToSpeech.speak(lastAnswer, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        btnSubmit.setOnClickListener(v -> submitQuestion());

        View.OnClickListener chipListener = v -> {
            TextView t = (TextView) v;
            etQuestion.setText(t.getText().toString());
            if (currentBitmap != null) {
                submitQuestion();
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            }
        };

        chip1.setOnClickListener(chipListener);
        chip2.setOnClickListener(chipListener);
        chip3.setOnClickListener(chipListener);
    }

    private void checkPermissions() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (photoFile != null) {
            Uri photoURI = FileProvider.getUriForFile(this,
                    "com.example.vqa.fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            try {
                startActivityForResult(takePictureIntent, CAMERA_REQUEST_CODE);
            } catch (Exception e) {
                Toast.makeText(this, "Camera app not found", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void startSpeechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about your image...");
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not supported", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == GALLERY_REQUEST_CODE && data != null) {
                currentImageUri = data.getData();
                loadImageFromUri(currentImageUri);
            } else if (requestCode == CAMERA_REQUEST_CODE) {
                File f = new File(currentPhotoPath);
                currentImageUri = Uri.fromFile(f);
                loadImageFromUri(currentImageUri);
            } else if (requestCode == SPEECH_REQUEST_CODE && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    etQuestion.setText(matches.get(0));
                    if (currentBitmap != null) {
                        submitQuestion();
                    }
                }
            }
        }
    }

    private void loadImageFromUri(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, options);
            is.close();
            
            int scale = 1;
            while (options.outWidth / scale / 2 >= 800 && options.outHeight / scale / 2 >= 800) {
                scale *= 2;
            }
            
            BitmapFactory.Options scaleOptions = new BitmapFactory.Options();
            scaleOptions.inSampleSize = scale;
            is = getContentResolver().openInputStream(uri);
            currentBitmap = BitmapFactory.decodeStream(is, null, scaleOptions);
            is.close();
            
            ivPreview.setImageBitmap(currentBitmap);
            ivPreview.clearColorFilter(); // Remove the purple tint!
            ivPreview.getLayoutParams().width = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            ivPreview.getLayoutParams().height = 800; // Make the preview larger
            ivPreview.requestLayout();
            
            tvDropText.setVisibility(View.GONE);
            tvFormats.setVisibility(View.GONE);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private void submitQuestion() {
        String question = etQuestion.getText().toString().trim();
        if (currentBitmap == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (question.isEmpty()) {
            Toast.makeText(this, "Please ask a question", Toast.LENGTH_SHORT).show();
            return;
        }

        tvAnswer.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);

        new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] imageBytes = baos.toByteArray();

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("question", question)
                        .addFormDataPart("image", "image.jpg",
                                RequestBody.create(imageBytes, MediaType.parse("image/jpeg")))
                        .build();

                Request request = new Request.Builder()
                        .url("https://avinavpri-vqa-backend.hf.space/predict")
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSubmit.setEnabled(true);
                            tvAnswer.setText("Error: " + e.getMessage());
                            tvAnswer.setVisibility(View.VISIBLE);
                        });
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String respStr = response.body().string();
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            btnSubmit.setEnabled(true);
                            try {
                                if (response.isSuccessful()) {
                                    JSONObject json = new JSONObject(respStr);
                                    String answer = json.getJSONArray("predictions").getJSONObject(0).getString("answer");
                                    lastAnswer = answer;
                                    tvAnswer.setText(answer);
                                    btnTts.setVisibility(View.VISIBLE);
                                    textToSpeech.speak(answer, TextToSpeech.QUEUE_FLUSH, null, null);
                                } else {
                                    tvAnswer.setText("Server Error: " + response.code());
                                }
                            } catch (JSONException e) {
                                tvAnswer.setText("Failed to parse response: " + respStr);
                            }
                            tvAnswer.setVisibility(View.VISIBLE);
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(MainActivity.this, "Error processing image", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
