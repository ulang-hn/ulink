package com.ulang.libulink.p2p;

import android.text.TextUtils;
import android.util.Log;


import com.ulang.libulink.utils.Constants;
import com.ulang.libulink.utils.KLog;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import owt.base.ActionCallback;
import owt.base.AudioEncodingParameters;
import owt.base.LocalStream;
import owt.base.MediaConstraints;
import owt.base.OwtError;
import owt.base.VideoEncodingParameters;
import owt.p2p.P2PClient;
import owt.p2p.P2PClientConfiguration;
import owt.p2p.Publication;
import owt.p2p.RemoteStream;
import owt.utils.OwtVideoCapturer;

import static org.webrtc.PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
import static owt.base.CheckCondition.RCHECK;
import static owt.base.Const.LOG_TAG;
import static owt.base.MediaCodecs.AudioCodec.OPUS;
import static owt.base.MediaCodecs.VideoCodec.H264;
import static owt.base.MediaCodecs.VideoCodec.H265;
import static owt.base.MediaCodecs.VideoCodec.VP8;
import static owt.base.MediaCodecs.VideoCodec.VP9;

public class P2PClientModel implements P2PClient.P2PClientObserver {
    public interface P2PClientModelObserver {
        void onConnected();

        void onServerDisconnected();

        void onConnectFailed(OwtError error);

        void onStreamAdded(String peerId, RemoteStream remoteStream);

        void onPublished(LocalStream localStream, Publication result);

        void onPublishFailed(OwtError error);

        void onCameraOpen(LocalStream localStream);

        void onCallAccept(String peerId);

        void onCallRefuse(String peerId);
    }

    public interface P2PCallReceiver {
        void onCallIn(String peerId);

        void onCallCancel(String peerId);
    }

    private volatile static P2PClientModel INSTANCE = null;
    private final List<P2PClientModelObserver> observers;
    private P2PClient p2PClient;
    private OwtVideoCapturer owtVideoCapturer;
    private LocalStream localStream;
    private Publication publication;
    private P2PCallReceiver p2PCallReceiver;

    public static P2PClientModel getInstance() {
        if (INSTANCE == null) {
            synchronized (P2PClientModel.class) {
                if (INSTANCE == null) {
                    INSTANCE = new P2PClientModel();
                }
            }
        }
        return INSTANCE;
    }

    public P2PClientModel() {
        observers = Collections.synchronizedList(new ArrayList<>());
        initP2PClient();
    }

    public void addObserver(P2PClientModelObserver observer) {
        RCHECK(observer);
        if (observers.contains(observer)) {
            Log.w(LOG_TAG, "Skipped adding a duplicated observer.");
            return;
        }
        observers.add(observer);
    }

    public void removeObserver(P2PClientModelObserver observer) {
        RCHECK(observer);
        observers.remove(observer);
    }

    public void registerCallReceiver(P2PCallReceiver p2PCallReceiver) {
        RCHECK(p2PCallReceiver);
        this.p2PCallReceiver = p2PCallReceiver;
    }

    public void unRegisterCallReceiver() {
        RCHECK(p2PCallReceiver);
        this.p2PCallReceiver = null;
    }

    private void initP2PClient() {
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder(
                "stun:stun.xten.com")
                .createIceServer();
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(iceServer);
        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(
                iceServers);
        rtcConfiguration.continualGatheringPolicy = GATHER_CONTINUALLY;

        VideoEncodingParameters h264 = new VideoEncodingParameters(H264);
        VideoEncodingParameters vp8 = new VideoEncodingParameters(VP8);
        VideoEncodingParameters vp9 = new VideoEncodingParameters(VP9);
        VideoEncodingParameters h265 = new VideoEncodingParameters(H265);
        AudioEncodingParameters opus = new AudioEncodingParameters(OPUS);
        P2PClientConfiguration configuration = P2PClientConfiguration.builder()
                .addVideoParameters(vp9)
                .addAudioParameters(opus)
                //.setRTCConfiguration(rtcConfiguration)
                .build();

        p2PClient = new P2PClient(configuration, new SocketSignalingChannel());
        p2PClient.addObserver(this);
    }

    @Override
    public void onServerDisconnected() {
        KLog.e();
        for (P2PClientModelObserver observer : observers) {
            observer.onServerDisconnected();
        }
    }

    @Override
    public void onStreamAdded(String peerId, RemoteStream remoteStream) {
        KLog.e();
        for (P2PClientModelObserver observer : observers) {
            observer.onStreamAdded(peerId, remoteStream);
        }
    }

    @Override
    public void onDataReceived(String peerId, String message) {
        KLog.e("from:" + peerId + ",msg:" + message);
        if (observers.size() == 0 && p2PCallReceiver != null) {
            //呼叫
            if (Constants.P2P_MSG_CALL_IN.equals(message)) {
                p2PCallReceiver.onCallIn(peerId);
            } else if (Constants.P2P_MSG_CALL_CANCEL.equals(message)) {
                p2PCallReceiver.onCallCancel(peerId);
            }
            return;
        }

        if (Constants.P2P_MSG_CALL_ACCEPT.equals(message)) {
            for (P2PClientModelObserver observer : observers) {
                observer.onCallAccept(peerId);
            }
        } else if (Constants.P2P_MSG_CALL_REFUSE.equals(message)) {
            for (P2PClientModelObserver observer : observers) {
                observer.onCallRefuse(peerId);
            }
        }

    }

    public void connectAndPublic(String userId, String peerId) {
        connect(userId,peerId);
    }

    public void connect(String userId) {
        connect(userId,null);
    }

    private void connect(String userId, String peerId) {

        JSONObject loginObj = new JSONObject();
        try {
            loginObj.put("host", Constants.P2P_BASE_URL);
            loginObj.put("token", userId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        p2PClient.addAllowedRemotePeer(userId);
        p2PClient.connect(loginObj.toString(), new ActionCallback<String>() {
            @Override
            public void onSuccess(String result) {
                KLog.e("connect onSuccess" + result);
                for (P2PClientModelObserver observer : observers) {
                    observer.onConnected();
                }
                if (peerId != null) {
                    publish(peerId);
                }
            }

            @Override
            public void onFailure(OwtError error) {
                KLog.e(error.errorMessage);
                for (P2PClientModelObserver observer : observers) {
                    observer.onConnectFailed(error);
                }
            }
        });
    }

    public void disconnect() {
        p2PClient.disconnect();
    }

    public void acceptCall(String peerId) {
        p2PClient.send(peerId, Constants.P2P_MSG_CALL_ACCEPT, null);
    }

    public void refuseCall(String peerId) {
        p2PClient.send(peerId, Constants.P2P_MSG_CALL_REFUSE, null);
    }

    public void call(String peerId) {
        p2PClient.send(peerId, Constants.P2P_MSG_CALL_IN, null);
    }

    public void cancelCall(String peerId) {
        p2PClient.send(peerId, Constants.P2P_MSG_CALL_CANCEL, new ActionCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                KLog.e();
            }

            @Override
            public void onFailure(OwtError error) {
                KLog.e(error.errorMessage);
            }
        });
    }

    public void publish(String peerId) {
        p2PClient.addAllowedRemotePeer(peerId);
        if (localStream == null) {
            return;
        }
        p2PClient.publish(peerId, localStream, new ActionCallback<Publication>() {
            @Override
            public void onSuccess(Publication result) {
                KLog.e("publish onSuccess");
                publication = result;
                for (P2PClientModelObserver observer : observers) {
                    observer.onPublished(localStream, result);
                }
            }

            @Override
            public void onFailure(OwtError error) {
                KLog.e(error.errorMessage);
                for (P2PClientModelObserver observer : observers) {
                    observer.onPublishFailed(error);
                }
                if (error.errorMessage.equals("Duplicated stream.")) {
                    //this mean you have published, so change the button to unpublish
                }
            }
        });
    }

    public void stop(String peerId) {
        if (publication != null) {
            publication.stop();
            publication = null;
        }
        if (owtVideoCapturer != null) {
            owtVideoCapturer.stopCapture();
            owtVideoCapturer.dispose();
            owtVideoCapturer = null;
        }
        if (localStream != null) {
            localStream.dispose();
            localStream = null;
        }
        p2PClient.stop(peerId);
    }

    public void initLocalStream(int width, int height, int framerate, boolean isCameraFront) {
        owtVideoCapturer = OwtVideoCapturer.create(width, height, framerate, true,
                isCameraFront);
        localStream = new LocalStream(owtVideoCapturer,
                new MediaConstraints.AudioTrackConstraints());
        for (P2PClientModelObserver observer : observers) {
            observer.onCameraOpen(localStream);
        }
    }

    public void openCamera() {
        owtVideoCapturer.startCapture(owtVideoCapturer.getWidth(), owtVideoCapturer.getHeight(), owtVideoCapturer.getFps());
    }

    public void closeCamera() {
        owtVideoCapturer.stopCapture();
    }
}
