package com.dogom.naesaekki;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.widget.RemoteViews;

import java.io.File;
import java.util.Calendar;

/** 홈 화면 위젯: 앱이 넘겨 준 이름·생일·사진으로 그리고, 나이는 그날 날짜로 계산 (평생 이용권 기능) */
public class PetWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, PetWidget.class));
        for (int id : ids) update(c, m, id);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        SharedPreferences p = c.getSharedPreferences("widget", Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_pet);
        String name = p.getString("name", "");
        String birth = p.getString("birth", "");
        boolean pro = p.getBoolean("pro", false);
        int ink = parse(p.getString("ink", ""), 0xFF242620);
        v.setTextColor(R.id.w_age, ink);
        v.setTextColor(R.id.w_name, (ink & 0x00FFFFFF) | 0xB3000000);
        if (name.isEmpty()) {
            v.setTextViewText(R.id.w_age, "내새끼");
            v.setTextViewText(R.id.w_name, "앱을 한 번 열어 주세요");
        } else if (!pro) {
            v.setTextViewText(R.id.w_age, "평생 이용권");
            v.setTextViewText(R.id.w_name, "위젯은 이용권으로 쓸 수 있어요");
        } else {
            v.setTextViewText(R.id.w_age, age(birth));
            v.setTextViewText(R.id.w_name, name);
            File f = new File(c.getFilesDir(), "widget.png");
            if (f.exists()) {
                Bitmap b = BitmapFactory.decodeFile(f.getPath());
                if (b != null) v.setImageViewBitmap(R.id.w_photo, b);
            }
        }
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i != null) {
            PendingIntent pi = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.w_root, pi);
        }
        m.updateAppWidget(id, v);
    }

    private static int parse(String hex, int def) {
        try { return Color.parseColor(hex); } catch (Exception e) { return def; }
    }

    /** "2023-07-04" → "3살 4개월" (앱과 같은 계산) */
    static String age(String birth) {
        try {
            String[] s = birth.split("-");
            int by = Integer.parseInt(s[0]), bm = Integer.parseInt(s[1]), bd = Integer.parseInt(s[2]);
            Calendar t = Calendar.getInstance();
            int months = (t.get(Calendar.YEAR) - by) * 12 + (t.get(Calendar.MONTH) + 1 - bm);
            if (t.get(Calendar.DAY_OF_MONTH) < bd) months--;
            if (months < 0) months = 0;
            int y = months / 12, mm = months % 12;
            if (y == 0) return mm + "개월";
            return mm == 0 ? y + "살" : y + "살 " + mm + "개월";
        } catch (Exception e) {
            return "";
        }
    }
}
