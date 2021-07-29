package com.ulang.ulink;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.ulang.libulink.p2p.P2PEngine;
import org.webrtc.SurfaceViewRenderer;


public class P2PActivity extends AppCompatActivity implements P2PEngine.P2PReceiver {
    private String peerId;
    private SurfaceViewRenderer sv1;
    private SurfaceViewRenderer sv2;

    @Override
    protected void onCreate(@Nullable @org.jetbrains.annotations.Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_p2p);
        peerId = getIntent().getStringExtra("peerId");
        sv1 = findViewById(R.id.sv_1);
        sv2 = findViewById(R.id.sv_2);
        SApplication.getP2PEngine().registerReceiver(this);
        SApplication.getP2PEngine().startTalk(peerId,sv1,sv2);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SApplication.getP2PEngine().leave();
    }

    @Override
    public void onCallIn(String peerId) {

    }

    @Override
    public void onCallCancel(String peerId) {

    }

    @Override
    public void onCallAccept(String peerId) {

    }

    @Override
    public void onCallRefuse(String peerId) {

    }

    @Override
    public void onLeave(String peerId) {

    }

    @Override
    public void onServerConnected() {

    }

    @Override
    public void onServerDisconnected() {

    }

    @Override
    public void onServerConnectFailed(String error) {

    }

    @Override
    public void onPublishError(String error) {

    }
}
