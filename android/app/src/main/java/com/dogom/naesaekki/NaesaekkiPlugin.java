package com.dogom.naesaekki;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ClipData;
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
import android.provider.OpenableColumns;
import android.util.Base64;

import androidx.activity.result.ActivityResult;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /** 휴대폰 갤러리를 바로 열어 사진 여러 장 고르기 */
    @PluginMethod
    public void pickImages(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (i.resolveActivity(getContext().getPackageManager()) == null) {
            i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        try {
            startActivityForResult(call, i, "imagesPicked");
        } catch (Exception e) {
            call.reject("no gallery", e);
        }
    }

    @ActivityCallback
    private void imagesPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent d = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || d == null) { call.reject("cancelled", "CANCELLED"); return; }
        List<Uri> uris = new ArrayList<>();
        ClipData cd = d.getClipData();
        if (cd != null) {
            for (int k = 0; k < cd.getItemCount(); k++) {
                Uri u = cd.getItemAt(k).getUri();
                if (u != null) uris.add(u);
            }
        } else if (d.getData() != null) {
            uris.add(d.getData());
        }
        ContentResolver cr = getContext().getContentResolver();
        JSArray files = new JSArray();
        for (Uri u : uris) {
            String mime = cr.getType(u);
            if (mime != null && !mime.startsWith("image/")) continue;
            String name = "photo.jpg";
            long mod = 0;
            try (Cursor c = cr.query(u, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni);
                    int ti = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
                    if (ti >= 0 && !c.isNull(ti)) mod = c.getLong(ti) * 1000L;
                }
            } catch (Exception ignored) { }
            JSObject f = new JSObject();
            f.put("uri", u.toString());
            f.put("name", name);
            f.put("mime", mime != null ? mime : "image/jpeg");
            f.put("modified", mod);
            files.put(f);
        }
        JSObject r = new JSObject();
        r.put("files", files);
        call.resolve(r);
    }

    /* ---------- 구글 플레이 결제: 평생 이용권 (한 번 결제하는 인앱 상품) ---------- */
    private BillingClient billing;
    private PluginCall buyCall;

    private interface Ready { void run(); }

    /** 결제 서비스에 연결된 뒤 할 일 실행 */
    private void withBilling(PluginCall call, Ready then) {
        if (billing == null) {
            billing = BillingClient.newBuilder(getContext())
                    .setListener((result, purchases) -> onPurchases(result, purchases))
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .build();
        }
        if (billing.isReady()) { then.run(); return; }
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) then.run();
                else call.reject("billing unavailable: " + r.getDebugMessage(), "UNAVAILABLE");
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    private void queryProduct(String id, PluginCall call, java.util.function.Consumer<ProductDetails> then) {
        QueryProductDetailsParams q = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id).setProductType(BillingClient.ProductType.INAPP).build()))
                .build();
        billing.queryProductDetailsAsync(q, (r, list) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null || list.isEmpty()) {
                call.reject("product not found", "NO_PRODUCT");
                return;
            }
            then.accept(list.get(0));
        });
    }

    /** 상품 가격 가져오기: {price:"₩5,900"} */
    @PluginMethod
    public void billingProduct(PluginCall call) {
        String id = call.getString("id", "pro_lifetime");
        withBilling(call, () -> queryProduct(id, call, pd -> {
            JSObject r = new JSObject();
            ProductDetails.OneTimePurchaseOfferDetails o = pd.getOneTimePurchaseOfferDetails();
            r.put("price", o != null ? o.getFormattedPrice() : "");
            r.put("title", pd.getName());
            call.resolve(r);
        }));
    }

    /** 구매 창 열기 → {state:"purchased"|"pending"} */
    @PluginMethod
    public void billingBuy(PluginCall call) {
        String id = call.getString("id", "pro_lifetime");
        withBilling(call, () -> queryProduct(id, call, pd -> {
            BillingFlowParams fp = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd).build()))
                    .build();
            buyCall = call;
            getActivity().runOnUiThread(() -> {
                BillingResult r = billing.launchBillingFlow(getActivity(), fp);
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                    buyCall = null;
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        JSObject o = new JSObject(); o.put("state", "purchased"); call.resolve(o);
                    } else call.reject("launch failed: " + r.getDebugMessage(), "FAILED");
                }
            });
        }));
    }

    private void onPurchases(BillingResult r, List<Purchase> purchases) {
        PluginCall c = buyCall;
        buyCall = null;
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            String state = "none";
            for (Purchase p : purchases) {
                if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) { ack(p); state = "purchased"; }
                else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING && !"purchased".equals(state)) state = "pending";
            }
            if (c != null) { JSObject o = new JSObject(); o.put("state", state); c.resolve(o); }
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            if (c != null) { JSObject o = new JSObject(); o.put("state", "purchased"); c.resolve(o); }
        } else if (c != null) {
            if (code == BillingClient.BillingResponseCode.USER_CANCELED) c.reject("cancelled", "CANCELLED");
            else c.reject("purchase failed: " + r.getDebugMessage(), "FAILED");
        }
    }

    private void ack(Purchase p) {
        if (p.isAcknowledged()) return;
        billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), res -> { });
    }

    /** 구매 복원: 이 구글 계정이 이용권을 가지고 있는지 → {owned:true|false, pending:bool} */
    @PluginMethod
    public void billingRestore(PluginCall call) {
        String id = call.getString("id", "pro_lifetime");
        withBilling(call, () -> billing.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (r, list) -> {
                    if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) { call.reject("query failed", "FAILED"); return; }
                    boolean owned = false, pending = false;
                    if (list != null) for (Purchase p : list) {
                        if (!p.getProducts().contains(id)) continue;
                        if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) { owned = true; ack(p); }
                        else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) pending = true;
                    }
                    JSObject o = new JSObject(); o.put("owned", owned); o.put("pending", pending); call.resolve(o);
                }));
    }

    /** 홈 화면 위젯에 보여 줄 내용 저장하고 위젯 새로 그리기 */
    @PluginMethod
    public void updateWidget(PluginCall call) {
        try {
            android.content.SharedPreferences.Editor e = getContext().getSharedPreferences("widget", android.content.Context.MODE_PRIVATE).edit();
            e.putString("name", call.getString("name", ""));
            e.putString("birth", call.getString("birth", ""));
            e.putBoolean("pro", Boolean.TRUE.equals(call.getBoolean("pro", false)));
            e.putString("ink", call.getString("ink", ""));
            e.apply();
            java.io.File f = new java.io.File(getContext().getFilesDir(), "widget.png");
            String photo = call.getString("photo", "");
            if (photo != null && !photo.isEmpty()) {
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(f)) { os.write(Base64.decode(photo, Base64.DEFAULT)); }
            } else if (f.exists()) {
                f.delete();
            }
            PetWidget.updateAll(getContext());
            call.resolve();
        } catch (Exception ex) {
            call.reject("widget failed", ex);
        }
    }
}
