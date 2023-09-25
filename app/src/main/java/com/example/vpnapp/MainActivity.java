package com.example.vpnapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    Button startVPN;
    private String ServerIP;
    private int ServerPortNumber;
    private static final int VPN_REQUEST_CODE = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startVPN = findViewById(R.id.startVPN);
        startVPN.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                establishedVpnConnection();
            }
        });
        LocalBroadcastManager.getInstance(this).registerReceiver(VpnConnectedReceiver,
                new IntentFilter(MyVpnService.ACTION_VPN_CONNECTED));

    }

    private void establishedVpnConnection() {
        Intent VpnIntent = VpnService.prepare(MainActivity.this);
        if(VpnIntent != null){
            startActivityForResult(VpnIntent, VPN_REQUEST_CODE);
        }else{
            startVpnServiceWithIp();
        }
    }
    private void startVpnServiceWithIp() {
        Intent vpnIntent = new Intent(MainActivity.this, MyVpnService.class);
        vpnIntent.putExtra("vpnIp", ServerIP);
        vpnIntent.putExtra(" ", ServerPortNumber);
        startService(vpnIntent);
    }
    private BroadcastReceiver VpnConnectedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

        }
    };
}