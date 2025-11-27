package com.example.aiassistantcoder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

public class LiveRunManager {

    public interface Listener {
        void log(String msg);

        void onSessionReady(String wsUrl, String sessionId);

        default void onStopped() {
        }

        void onError(String msg);
    }

    private final String baseUrl;      // e.g. http://10.0.2.2:8080
    private final ExecutorService exec;
    private final Listener listener;

    public LiveRunManager(@NonNull String baseUrl,
                          @NonNull ExecutorService exec,
                          @NonNull Listener listener) {
        this.baseUrl = baseUrl;
        this.exec = exec;
        this.listener = listener;
    }

    public void startLiveBulk(@NonNull JSONArray files,
                              @NonNull String language,
                              @NonNull String entrypoint,
                              boolean readOnlyFs,
                              boolean verifyUpload) {
        exec.execute(() -> {
            try {
                listener.log("⏵ creating project…\n");
                String projectId = createProject();

                listener.log("⏵ uploading " + files.length() + " file(s)…\n");
                uploadFilesBulk(projectId, files);

                // ✅ best-effort verification of each uploaded file
                if (verifyUpload) {
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject f = files.getJSONObject(i);

                        // the server only requires "path" + "content"
                        String rawPath = f.optString("path", "");
                        String filename = f.optString("filename", "");

                        String relPath;

                        if (!rawPath.isEmpty()) {
                            if (!rawPath.endsWith("/")) {
                                if (!filename.isEmpty()) {
                                    relPath = rawPath + "/" + filename;
                                } else {
                                    relPath = rawPath;
                                }
                            } else {
                                relPath = rawPath + filename;
                            }
                        } else {
                            relPath = filename;
                        }

                        if (relPath != null && !relPath.isEmpty()) {
                            verifyFileExists(projectId, relPath);
                        }
                    }
                }


                listener.log("⏵ starting session (" + entrypoint + ")…\n");
                JSONObject session = startSession(projectId, language, entrypoint, readOnlyFs);

                String sessionId = session.optString("id", null);
                String wsPath = session.optString("ws", null);
                if (wsPath == null) {
                    throw new RuntimeException("Bad /session response: no ws");
                }

                String wsUrl = toWsUrl(wsPath);
                listener.log("✅ session ready\n");
                listener.onSessionReady(wsUrl, sessionId);

            } catch (Exception e) {
                listener.onError("Live session error: " + e.getMessage());
            }
        });
    }


    /**
     * stop/cleanup remote container
     */
    public void stopSession(@Nullable String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        exec.execute(() -> {
            try {
                httpDelete(baseUrl + "/session/" + sessionId);
                listener.log("⏹ remote session closed\n");
                listener.onStopped();
            } catch (Exception e) {
                // not fatal, just log
                listener.onError("stop failed: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------------
    //  internal HTTP helpers (moved from fragment)
    // ------------------------------------------------------------------------

    private String createProject() throws Exception {
        JSONObject res = httpPostJson(baseUrl + "/projects", new JSONObject());
        String id = res.optString("projectId", null);
        if (id == null) throw new RuntimeException("No projectId");
        return id;
    }

    private void uploadFile(@NonNull String projectId,
                            @NonNull String relPath,
                            @NonNull String content) throws Exception {
        JSONObject body = new JSONObject();
        body.put("path", relPath);
        body.put("content", content);
        httpPutJson(baseUrl + "/projects/" + projectId + "/files", body);
    }

    private void uploadFilesBulk(@NonNull String projectId,
                                 @NonNull JSONArray files) throws Exception {
        JSONObject body = new JSONObject();
        body.put("files", files);
        httpPostJson(baseUrl + "/projects/" + projectId + "/files/bulk", body);
    }

    private boolean verifyFileExists(@NonNull String projectId,
                                     @NonNull String relPath) {
        try {
            String encodedPath;
            // Old overload: works on all API levels you support
            encodedPath = URLEncoder.encode(relPath, StandardCharsets.UTF_8);

            String url = baseUrl + "/projects/" + projectId + "/files?path=" + encodedPath;
            JSONObject obj = httpGetJsonObject(url);
            String c = obj.optString("content", null);
            if (c != null) {
                listener.log("Uploaded " + relPath + " (" + c.length() + " bytes)\n");
                return true;
            }
        } catch (Exception e) {
            listener.onError("verifyFileExists: " + e.getMessage());
        }
        return false;
    }


    private JSONObject startSession(@NonNull String projectId,
                                    @NonNull String language,
                                    @NonNull String mainPath,
                                    boolean readOnlyFs) throws Exception {
        JSONObject body = new JSONObject();
        body.put("language", language);
        // your backend: cmd OR mainPath. You showed mainPath in your manager,
        // but original server uses 'cmd' for python, so here we assume mainPath support.
        body.put("mainPath", mainPath);
        body.put("projectId", projectId);
        body.put("readOnlyFs", readOnlyFs);
        return httpPostJson(baseUrl + "/session", body);
    }

    private String toWsUrl(String wsPath) {
        return (baseUrl.startsWith("https://")
                ? baseUrl.replaceFirst("^https://", "wss://")
                : baseUrl.replaceFirst("^http://", "ws://")) + wsPath;
    }

    // ---------- raw HTTP ----------
    private JSONObject httpGetJsonObject(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.connect();
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();
        if (code < 200 || code >= 300)
            throw new RuntimeException("GET " + urlStr + " -> " + code + ": " + body);
        return new JSONObject(body);
    }

    private JSONObject httpPostJson(String urlStr, JSONObject json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        writer.write(json.toString());
        writer.flush();
        writer.close();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();
        if (code < 200 || code >= 300)
            throw new RuntimeException("POST " + urlStr + " -> " + code + ": " + body);
        return new JSONObject(body);
    }

    private JSONObject httpPutJson(String urlStr, JSONObject json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        writer.write(json.toString());
        writer.flush();
        writer.close();
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();
        if (code < 200 || code >= 300)
            throw new RuntimeException("PUT " + urlStr + " -> " + code + ": " + body);
        return new JSONObject(body);
    }

    private void httpDelete(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.connect();
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                while (br.readLine() != null) { /* drain */ }
            }
        }
        conn.disconnect();
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }
}
