package com.dogom.naesaekki;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NaesaekkiPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
