package com.ulang.libulink.p2p;

import android.util.Log;

import com.ulang.libulink.common.OwtContext;
import com.ulang.libulink.utils.Constants;
import com.ulang.libulink.utils.KLog;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;

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
        /**
         * 服务连接成功
         */
        void onServerConnected();

        /**
         * 服务连接失败
         *
         * @param error
         */
        void onServerConnectFailed(OwtError error);

        /**
         * 服务断开
         */
        void onServerDisconnected();

        /**
         * 接受到用户peerId的视频流
         *
         * @param peerId
         * @param remoteStream 远程视频流
         */
        void onStreamAdded(String peerId, RemoteStream remoteStream);

        /**
         * 用户peerId停止发布视频
         *
         * @param peerId
         */
        void onLeft(String peerId);

        /**
         * 发布视频成功
         *
         * @param result
         */
        void onPublished(Publication result);

        /**
         * 发布视频失败
         *
         * @param error
         */
        void onPublishFailed(OwtError error);

        /**
         * 摄像头打开，获取到本地视频流
         *
         * @param localStream 本地视频流
         */
        void onCameraOpen(LocalStream localStream);
    }

    public interface P2PCallReceiver {
        /**
         * 用户peerId呼叫呼入
         *
         * @param peerId
         */
        void onCallIn(String peerId);

        /**
         * 用户peerId呼叫取消
         *
         * @param peerId
         */
        void onCallCancel(String peerId);

        /**
         * 用户peerId接受呼叫
         *
         * @param peerId
         */
        void onCallAccept(String peerId);

        /**
         * 用户peerId拒绝呼叫
         *
         * @param peerId
         */
        void onCallRefuse(String peerId);
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
                "stun:stun.voipbuster.com")
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
                .setRTCConfiguration(rtcConfiguration)
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
        remoteStream.addObserver(new owt.base.RemoteStream.StreamObserver() {
            @Override
            public void onEnded() {
                for (P2PClientModelObserver observer : observers) {
                    observer.onLeft(peerId);
                }
            }

            @Override
            public void onUpdated() {

            }
        });
    }

    @Override
    public void onDataReceived(String peerId, String message) {
        KLog.e("from:" + peerId + ",msg:" + message);
        if (p2PCallReceiver != null) {
            //呼叫
            if (Constants.P2P_MSG_CALL_IN.equals(message)) {
                p2PCallReceiver.onCallIn(peerId);
            } else if (Constants.P2P_MSG_CALL_CANCEL.equals(message)) {
                p2PCallReceiver.onCallCancel(peerId);
            } else if (Constants.P2P_MSG_CALL_ACCEPT.equals(message)) {
                p2PCallReceiver.onCallAccept(peerId);
            } else if (Constants.P2P_MSG_CALL_REFUSE.equals(message)) {
                p2PCallReceiver.onCallRefuse(peerId);
            }
        }
    }

    public void connectAndPublic(String userId, String peerId) {
        connect(userId, peerId);
    }

    public void connect(String userId) {
        connect(userId, null);
    }

    private void connect(String userId, String peerId) {

        JSONObject loginObj = new JSONObject();
        try {
            loginObj.put("host", Constants.P2P_BASE_URL);
            loginObj.put("token", userId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        p2PClient.connect(loginObj.toString(), new ActionCallback<String>() {
            @Override
            public void onSuccess(String result) {
                KLog.e("connect onSuccess" + result);
                for (P2PClientModelObserver observer : observers) {
                    observer.onServerConnected();
                }
                if (peerId != null) {
                    publish(peerId);
                }
            }

            @Override
            public void onFailure(OwtError error) {
                KLog.e(error.errorMessage);
                for (P2PClientModelObserver observer : observers) {
                    observer.onServerConnectFailed(error);
                }
            }
        });
    }

    public void disconnect() {
        p2PClient.disconnect();
    }

    /**
     * 接受呼叫邀请
     *
     * @param peerId
     * @param callback
     */
    public void acceptCall(String peerId, ActionCallback<Void> callback) {
        p2PClient.sendCallMsg(peerId, Constants.P2P_MSG_CALL_ACCEPT, callback);
    }

    /**
     * 拒绝呼叫邀请
     *
     * @param peerId
     * @param callback
     */
    public void refuseCall(String peerId, ActionCallback<Void> callback) {
        p2PClient.sendCallMsg(peerId, Constants.P2P_MSG_CALL_REFUSE, callback);
    }

    /**
     * 呼叫
     *
     * @param peerId
     * @param callback
     */
    public void call(String peerId, ActionCallback<Void> callback) {
        p2PClient.sendCallMsg(peerId, Constants.P2P_MSG_CALL_IN, callback);
    }

    /**
     * 取消呼叫
     *
     * @param peerId
     * @param callback
     */
    public void cancelCall(String peerId, ActionCallback<Void> callback) {
        p2PClient.sendCallMsg(peerId, Constants.P2P_MSG_CALL_CANCEL, callback);
    }

    /**
     * 发布视频流
     *
     * @param peerId
     */
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
                    observer.onPublished(result);
                }
            }

            @Override
            public void onFailure(OwtError error) {
                KLog.e(error.errorMessage == null ? "" : error.errorMessage);
                for (P2PClientModelObserver observer : observers) {
                    observer.onPublishFailed(error);
                }
                if ("Duplicated stream.".equals(error.errorMessage)) {
                    //this mean you have published, so change the button to unpublish
                }
            }
        });
    }

    public void stop(String peerId) {
        KLog.e("stop " + peerId);
        p2PClient.stop(peerId);
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
    }

    /**
     * 打开摄像头
     *
     * @param width
     * @param height
     * @param framerate
     * @param isCameraFront
     */
    public void startCapture(int width, int height, int framerate, boolean isCameraFront) {
        owtVideoCapturer = OwtVideoCapturer.create(width, height, framerate, true,
                isCameraFront);
        localStream = new LocalStream(owtVideoCapturer,
                new MediaConstraints.AudioTrackConstraints());
        for (P2PClientModelObserver observer : observers) {
            observer.onCameraOpen(localStream);
        }
    }

    public void switchCamera() {
        owtVideoCapturer.switchCamera();
    }

    /**
     * 初始化 surfaceViewRenderer
     *
     * @param surfaceViewRenderer
     */
    public void initSurfaceViewRenderer(SurfaceViewRenderer surfaceViewRenderer) {
        surfaceViewRenderer.init(OwtContext.getInstance().getRootEglBase().getEglBaseContext()
                , null);
    }
}
