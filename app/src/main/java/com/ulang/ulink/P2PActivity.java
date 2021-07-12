package com.ulang.ulink;

import android.graphics.Point;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.ulang.libulink.p2p.P2PClientModel;
import com.ulang.libulink.utils.KLog;

import org.webrtc.SurfaceViewRenderer;

import owt.base.LocalStream;
import owt.base.OwtError;
import owt.p2p.Publication;
import owt.p2p.RemoteStream;

public class P2PActivity extends AppCompatActivity implements P2PClientModel.P2PClientModelObserver {
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

        P2PClientModel.getInstance().addObserver(this);
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(displaySize);
        P2PClientModel.getInstance().initSurfaceViewRenderer(sv1);
        P2PClientModel.getInstance().initSurfaceViewRenderer(sv2);
        P2PClientModel.getInstance().startCapture(displaySize.x, displaySize.y, 60, true);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        P2PClientModel.getInstance().stop(peerId);
        sv1.release();
        sv2.release();
    }

    @Override
    public void onServerConnected() {

    }

    @Override
    public void onServerDisconnected() {

    }

    @Override
    public void onServerConnectFailed(OwtError error) {

    }

    @Override
    public void onStreamAdded(String peerId, RemoteStream remoteStream) {
        KLog.e("onStreamAdded " + peerId + "," + remoteStream.hasVideo());
        remoteStream.detach(sv2);
        remoteStream.attach(sv2);
    }

    @Override
    public void onLeft(String peerId) {
        KLog.e("onLeft " + peerId);
        //finish();
    }

    @Override
    public void onPublished(Publication result) {

    }

    @Override
    public void onPublishFailed(OwtError error) {

    }

    @Override
    public void onCameraOpen(LocalStream localStream) {
        localStream.attach(sv1);
        P2PClientModel.getInstance().publish(peerId);
    }
}
