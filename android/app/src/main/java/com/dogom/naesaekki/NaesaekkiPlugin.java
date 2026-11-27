package com.dogom.naesaekki;

import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Base64;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** 내새끼 전용 기능: 갤러리에 바로 저장 · 폴더째 사진 고르기 */
@CapacitorPlugin(name = "Naesaekki")
public class NaesaekkiPlugin extends Plugin {

    /** PNG/JPEG(base64)를 사진 앱의 Pictures/내새끼 폴더에 바로 저장 (Android 10 이상) */
    @PluginMethod
    public void saveImage(PluginCall call) {
        String data = call.getString("data");
        String name = call.getString("name", "naesaekki.png");
        String mime = call.getString("mime", "image/png");
        if (data == null) { call.reject("no data"); return; }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { call.reject("unsupported"); return; }
        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            ContentResolver cr = getContext().getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, mime);
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/내새끼");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) { call.reject("insert failed"); return; }
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) throw new Exception("no stream");
                os.write(bytes);
            }
            v.clear();
            v.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            JSObject r = new JSObject();
            r.put("uri", uri.toString());
            call.resolve(r);
        } catch (Exception e) {
            call.reject("save failed", e);
        }
    }

    /** 폴더 고르기 → 안의 사진 목록(하위 폴더 포함) */
    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(call, i, "folderPicked");
    }

    @ActivityCallback
    private void folderPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent d = result.getData();
        if (d == null || d.getData() == null) { call.reject("cancelled", "CANCELLED"); return; }
        Uri tree = d.getData();
        JSArray files = new JSArray();
        try {
            list(tree, DocumentsContract.getTreeDocumentId(tree), files, 0);
        } catch (Exception e) {
            call.reject("list failed", e); return;
        }
        JSObject r = new JSObject();
        r.put("files", files);
        call.resolve(r);
    }

    private void list(Uri tree, String docId, JSArray out, int depth) {
        if (depth > 4 || out.length() > 2000) return;
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        String[] cols = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor c = getContext().getContentResolver().query(children, cols, null, null, null)) {
            if (c == null) return;
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                long mod = c.isNull(3) ? 0 : c.getLong(3);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    list(tree, id, out, depth + 1);
                } else if (mime != null && mime.startsWith("image/")) {
                    JSObject f = new JSObject();
                    f.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                    f.put("name", name);
                    f.put("mime", mime);
                    f.put("modified", mod);
                    out.put(f);
                }
            }
        }
    }

    /** 고른 폴더 안 사진 하나를 base64로 읽기 */
    @PluginMethod
    public void readFile(PluginCall call) {
        String u = call.getString("uri");
        if (u == null) { call.reject("no uri"); return; }
        try (InputStream is = getContext().getContentResolver().openInputStream(Uri.parse(u))) {
            if (is == null) throw new Exception("no stream");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
            JSObject r = new JSObject();
            r.put("data", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
            call.resolve(r);
        } catch (Exception e) {
            call.reject("read failed", e);
        }
    }

    /** 꾸민 화면을 바로 휴대폰 배경화면으로 지정 (which: home | lock | both) */
    @PluginMethod
    public void setWallpaper(PluginCall call) {
        String data = call.getString("data");
        String which = call.getString("which", "both");
        if (data == null) { call.reject("no data"); return; }
        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bm == null) { call.reject("decode failed"); return; }
            int flags = "home".equals(which) ? WallpaperManager.FLAG_SYSTEM
                    : "lock".equals(which) ? WallpaperManager.FLAG_LOCK
                    : (WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
            WallpaperManager.getInstance(getContext()).setBitmap(bm, null, true, flags);
            call.resolve();
        } catch (Exception e) {
            call.reject("set failed", e);
        }
    }
}
