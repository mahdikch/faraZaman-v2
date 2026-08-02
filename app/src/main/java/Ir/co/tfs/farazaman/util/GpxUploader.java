
package Ir.co.tfs.farazaman.util;

import android.util.Log;

import java.io.File;
import java.io.IOException;

import Ir.co.tfs.farazaman.AppConstants;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GpxUploader{

    public interface UploadCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void uploadFile(File zipFile, String token, UploadCallback callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", zipFile.getName(),
                        RequestBody.create(zipFile, MediaType.parse("application/zip")))
                .build();

        Request request = new Request.Builder()
                .url(AppConstants.BASE_URL + "api/GisGeolocation/upload")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", token)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("GpxUploader", "Upload failed: " + e.getMessage());
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onFailure("Server error: " + response.message());
                }
            }
        });
    }
}
