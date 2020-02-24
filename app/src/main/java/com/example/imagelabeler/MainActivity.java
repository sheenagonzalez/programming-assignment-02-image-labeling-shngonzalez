package com.example.imagelabeler;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import com.github.kittinunf.fuel.Fuel;
import com.github.kittinunf.fuel.core.FuelError;
import com.github.kittinunf.fuel.core.Request;
import com.github.kittinunf.fuel.core.Response;
import com.github.kittinunf.fuel.core.ResponseHandler;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;
    ImageView imageView;
    Button takePhotoButton;
    TextView infoText;
    int viewWidth;
    int viewHeight;
    CustomDrawableView customDrawableView;
    Canvas drawableCanvas;
    FrameLayout frameLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = (ImageView) findViewById(R.id.imageView);
        takePhotoButton = (Button) findViewById(R.id.takePhotoButton);
        infoText = (TextView) findViewById(R.id.infoText);

        drawableCanvas = new Canvas();
        frameLayout = findViewById(R.id.container);

        takePhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePictureIntent();
            }
        });

        try {
            Os.setenv("GOOGLE_APPLICATION_CREDENTIALS", "/sdcard/imagelabeler-269023-3f99973c0a69.json", true);
        } catch (ErrnoException e) {
            e.printStackTrace();
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    public class CustomDrawableView extends View {

        int left;
        int top;
        int width;
        int height;
        String name;

        public CustomDrawableView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            Rect rectangle = new Rect();
            rectangle.set(left, top, width, height);
            Rect textBounds = new Rect();
            textBounds.set(left, top, width, height);

            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setColor(Color.CYAN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setTextSize(30);

            canvas.drawRect(rectangle, paint);
            canvas.drawText(name, left, top, paint);
            invalidate();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            imageView.setImageBitmap(imageBitmap);

            viewWidth = imageView.getMeasuredWidth();
            viewHeight = imageView.getMeasuredHeight();

            frameLayout.removeAllViews();

            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            imageBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteStream);

            String base64Data = Base64.encodeToString(byteStream.toByteArray(), Base64.URL_SAFE);
            String requestURL = "https://vision.googleapis.com/v1/images:annotate?key=" + getResources().getString(R.string.mykey);

            try {
                // Create an array containing
                // the OBJECT_LOCALIZATION feature
                JSONArray features = new JSONArray();
                JSONObject feature = new JSONObject();
                feature.put("type", "OBJECT_LOCALIZATION");
                features.put(feature);


                // Create an object containing
                // the Base64-encoded image data
                JSONObject imageContent = new JSONObject();
                imageContent.put("content", base64Data);

                // Put the array and object into a single request
                // and then put the request into an array of requests
                JSONArray requests = new JSONArray();
                JSONObject request = new JSONObject();
                request.put("image", imageContent);
                request.put("features", features);
                requests.put(request);
                JSONObject postData = new JSONObject();
                postData.put("requests", requests);

                // Convert the JSON into a
                // string
                String body = postData.toString();

                Fuel.INSTANCE.post(requestURL, null)
                        .header("content-length", body.length())
                        .appendHeader("content-type", "application/json")
                        .body(body.getBytes(), Charset.defaultCharset())
                        .responseString(new ResponseHandler<String>() {
                            @Override
                            public void success(@NotNull Request request,
                                                @NotNull Response response,
                                                String data) {
                                // Access the localizedObjectAnnotations array
                                try {
                                    JSONArray objects = new JSONObject(data)
                                            .getJSONArray("responses")
                                            .getJSONObject(0)
                                            .getJSONArray("localizedObjectAnnotations");

                                    String info = "";
                                    String name;

                                    // Loop through the array and extract the
                                    // name key and bounding vertices for each item
                                    ArrayList<ArrayList> objectsCoords = new ArrayList<>();
                                    for (int i = 0; i < objects.length(); i++) {
                                        name = objects.getJSONObject(i).getString("name");
                                        info = info +
                                                name +
                                                "\n";
                                        JSONArray vertices = objects.getJSONObject(i)
                                                .getJSONObject("boundingPoly")
                                                .getJSONArray("normalizedVertices");
                                        int left;
                                        int top;
                                        int right;
                                        int bottom;
                                        try {
                                            left = (int)(vertices.getJSONObject(3).getDouble("x")*viewWidth);
                                            top = (int)(vertices.getJSONObject(3).getDouble("y")*viewHeight);
                                            right = (int)(vertices.getJSONObject(1).getDouble("x")*viewWidth);
                                            bottom = (int)(vertices.getJSONObject(1).getDouble("y")*viewHeight);

                                            customDrawableView = new CustomDrawableView(getApplicationContext());
                                            frameLayout.addView(customDrawableView);
                                            customDrawableView.left = left + 200;
                                            customDrawableView.top = top;
                                            customDrawableView.width = right;
                                            customDrawableView.height = bottom;
                                            customDrawableView.name = name;

                                            customDrawableView.draw(drawableCanvas);
                                        } catch (JSONException e) {
                                            // If out of bounds, put name description only
                                            info = info +
                                                    name +
                                                    "\n";
                                        }

                                    }

                                    // Display the annotations inside the TextView
                                    infoText.setText(info);

                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void failure(@NotNull Request request,
                                                @NotNull Response response,
                                                @NotNull FuelError fuelError) {}
                        });
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
