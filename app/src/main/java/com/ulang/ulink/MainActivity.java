package com.ulang.ulink;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import com.ulang.libulink.p2p.P2PClientModel;
import com.ulang.libulink.utils.KLog;

import owt.base.ActionCallback;
import owt.base.OwtError;


public class MainActivity extends AppCompatActivity implements P2PClientModel.P2PCallReceiver {
    private EditText etUser;
    private EditText etCall;
    private String callInPeerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etUser = findViewById(R.id.et_user);
        etCall = findViewById(R.id.et_call);
        findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                P2PClientModel.getInstance().connect(etUser.getText().toString());
            }
        });
        findViewById(R.id.btn_call).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                P2PClientModel.getInstance().call(etCall.getText().toString(), new ActionCallback(){

                    @Override
                    public void onSuccess(Object result) {

                    }

                    @Override
                    public void onFailure(OwtError error) {
                        KLog.e(error.errorMessage);
                    }
                });
            }
        });
        findViewById(R.id.btn_call_accept).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                P2PClientModel.getInstance().acceptCall(callInPeerId, new ActionCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Intent intent = new Intent();
                        intent.setClass(MainActivity.this, P2PActivity.class);
                        intent.putExtra("peerId", callInPeerId);
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(OwtError error) {

                    }
                });
            }
        });
        findViewById(R.id.btn_call_refuse).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                P2PClientModel.getInstance().refuseCall(callInPeerId,null);
            }
        });
        P2PClientModel.getInstance().registerCallReceiver(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(R.id.ll_call_in).setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        P2PClientModel.getInstance().unRegisterCallReceiver();
        P2PClientModel.getInstance().disconnect();
    }

    @Override
    public void onCallIn(String peerId) {
        callInPeerId = peerId;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                findViewById(R.id.ll_call_in).setVisibility(View.VISIBLE);
            }
        });

    }

    @Override
    public void onCallCancel(String peerId) {

    }

    @Override
    public void onCallAccept(String peerId) {
        Intent intent = new Intent();
        intent.setClass(MainActivity.this, P2PActivity.class);
        intent.putExtra("peerId", etCall.getText().toString());
        intent.putExtra("isCaller", true);
        startActivity(intent);
    }

    @Override
    public void onCallRefuse(String peerId) {

    }
}