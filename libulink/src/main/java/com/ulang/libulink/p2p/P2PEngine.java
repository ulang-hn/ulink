package com.ulang.libulink.p2p;

import android.content.Context;

import com.ulang.libulink.common.CameraCaptureConfiguration;
import com.ulang.libulink.common.Constants;
import com.ulang.libulink.common.ULinkVideoProcessor;
import com.ulang.libulink.utils.KLog;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.EglBase;
import org.webrtc.PeerConnection;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoProcessor;
import org.webrtc.VideoSink;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.List;

import owt.base.ActionCallback;
import owt.base.AudioEncodingParameters;
import owt.base.ContextInitialization;
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
import static owt.base.MediaCodecs.AudioCodec.OPUS;
import static owt.base.MediaCodecs.VideoCodec.H264;
import static owt.base.MediaCodecs.VideoCodec.H265;
import static owt.base.MediaCodecs.VideoCodec.VP8;
import static owt.base.MediaCodecs.VideoCodec.VP9;

public class P2PEngine implements P2PClient.P2PClientObserver {
    public interface P2PReceiver {
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

        /**
         * 用户peerId停止发布视频
         *
         * @param peerId
         */
        void onLeave(String peerId);

        /**
         * 服务连接成功
         */
        void onServerConnected();

        /**
         * 服务断开
         */
        void onServerDisconnected();

        /**
         * 服务连接错误
         *
         * @param error
         */
        void onServerConnectFailed(String error);

        /**
         * 发布视频错误
         *
         * @param error
         */
        void onPublishError(String error);

    }

    private P2PClient p2PClient;
    private OwtVideoCapturer owtVideoCapturer;
    private LocalStream localStream;
    private RemoteStream remoteStream;
    private Publication publication;
    private P2PReceiver p2PReceiver;
    private String peerId;
    private SurfaceViewRenderer localRenderer;
    private SurfaceViewRenderer remoteRenderer;
    private EglBase rootEglBase;
    private String serverUrl;
    private CameraCaptureConfiguration cameraCaptureConfiguration;
    private boolean hasRemoteAttached;
    private AudioDeviceModule audioDeviceModule;

    P2PEngine(String serverUrl, CameraCaptureConfiguration cameraCaptureConfiguration
            , EglBase rootEglBase, AudioDeviceModule audioDeviceModule, P2PReceiver p2PReceiver) {
        RCHECK(serverUrl);
        this.serverUrl = serverUrl;
        this.cameraCaptureConfiguration = cameraCaptureConfiguration;
        this.rootEglBase = rootEglBase;
        this.p2PReceiver = p2PReceiver;
        this.audioDeviceModule = audioDeviceModule;
        initP2PClient();
    }

    public static final class Builder {
        private String serverUrl;
        private CameraCaptureConfiguration cameraCaptureConfiguration;
        private EglBase rootEglBase;
        private P2PReceiver p2PReceiver;
        private AudioDeviceModule audioDeviceModule;

        public Builder setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        public Builder setCameraCaptureConfiguration(CameraCaptureConfiguration cameraCaptureConfiguration) {
            this.cameraCaptureConfiguration = cameraCaptureConfiguration;
            return this;
        }

        public Builder setApplicationContext(Context applicationContext) {
            RCHECK(applicationContext);
            rootEglBase = EglBase.create();
            audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
                    .setSampleRate(16000)
                    //是否立体声（双通道）
                    .setUseStereoInput(false)
                    .createAudioDeviceModule();
            ContextInitialization.create()
                    .setApplicationContext(applicationContext)
                    .addIgnoreNetworkType(ContextInitialization.NetworkType.LOOPBACK)
                    .setVideoHardwareAccelerationOptions(
                            rootEglBase.getEglBaseContext(),
                            rootEglBase.getEglBaseContext())
                    .setCustomizedAudioDeviceModule(audioDeviceModule)
                    .initialize();
            return this;
        }

        public void setP2PReceiver(P2PReceiver p2PReceiver) {
            RCHECK(p2PReceiver);
            this.p2PReceiver = p2PReceiver;
        }

        public P2PEngine build() {
            return new P2PEngine(serverUrl, cameraCaptureConfiguration, rootEglBase, audioDeviceModule, p2PReceiver);
        }
    }


    public void registerReceiver(P2PReceiver p2PReceiver) {
        RCHECK(p2PReceiver);
        this.p2PReceiver = p2PReceiver;
    }

    public void unRegisterReceiver() {
        if (this.p2PReceiver != null) {
            this.p2PReceiver = null;
        }
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
        if (p2PReceiver != null) {
            p2PReceiver.onServerDisconnected();
        }
    }

    @Override
    public void onStreamAdded(String peerId, RemoteStream remoteStream) {
        KLog.e();
        remoteStream.addObserver(new owt.base.RemoteStream.StreamObserver() {
            @Override
            public void onEnded() {
                if (p2PReceiver != null) {
                    p2PReceiver.onLeave(peerId);
                }
            }

            @Override
            public void onUpdated() {

            }
        });
        if (remoteRenderer != null) {
            remoteStream.attach(remoteRenderer);
            hasRemoteAttached = true;
        }
        this.remoteStream = remoteStream;
    }

    @Override
    public void onDataReceived(String peerId, String message) {
        KLog.e("from:" + peerId + ",msg:" + message);
        if (p2PReceiver != null) {
            //呼叫
            if (Constants.P2P_MSG_CALL_IN.equals(message)) {
                p2PReceiver.onCallIn(peerId);
            } else if (Constants.P2P_MSG_CALL_CANCEL.equals(message)) {
                p2PReceiver.onCallCancel(peerId);
            } else if (Constants.P2P_MSG_CALL_ACCEPT.equals(message)) {
                p2PReceiver.onCallAccept(peerId);
            } else if (Constants.P2P_MSG_CALL_REFUSE.equals(message)) {
                p2PReceiver.onCallRefuse(peerId);
            }
        }
    }

    /**
     * 连接服务
     *
     * @param uid 本地注册唯一uid
     */
    public void connectServer(String uid) {
        RCHECK(uid);
        JSONObject loginObj = new JSONObject();
        try {
            loginObj.put("host", serverUrl);
            loginObj.put("token", uid);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        p2PClient.connect(loginObj.toString(), new ActionCallback<String>() {
            @Override
            public void onSuccess(String result) {
                KLog.e("connect onSuccess" + result);
                if (p2PReceiver != null) {
                    p2PReceiver.onServerConnected();
                }
            }

            @Override
            public void onFailure(OwtError error) {
                KLog.e(error.errorMessage);
                if (p2PReceiver != null) {
                    p2PReceiver.onServerConnectFailed(error.errorMessage);
                }
            }
        });
    }

    public void disconnectServer() {
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
    private void publish(String peerId) {
        p2PClient.addAllowedRemotePeer(peerId);
        p2PClient.publish(peerId, localStream, new ActionCallback<Publication>() {
            @Override
            public void onSuccess(Publication result) {
                KLog.e("publish onSuccess");
                publication = result;
            }

            @Override
            public void onFailure(OwtError error) {
                KLog.e(error.errorMessage == null ? "" : error.errorMessage);
                if ("Duplicated stream.".equals(error.errorMessage)) {
                    //this mean you have published, so change the button to unpublish
                }
                if (p2PReceiver != null) {
                    p2PReceiver.onPublishError(error.errorMessage);
                }
            }
        });
    }

    /**
     * 开始视频通话
     *
     * @param peerId         对方的uid
     * @param localRenderer  本地视频显示
     * @param remoteRenderer 远程视频显示
     */
    public void startTalk(String peerId, SurfaceViewRenderer localRenderer, SurfaceViewRenderer remoteRenderer) {
        this.peerId = peerId;
        RCHECK(localRenderer);
        initSurfaceViewRenderer(localRenderer);
        this.localRenderer = localRenderer;
        setRemoteRenderer(remoteRenderer);
        if (cameraCaptureConfiguration != null) {
            startCapture(cameraCaptureConfiguration.getWidth()
                    , cameraCaptureConfiguration.getHeight(), cameraCaptureConfiguration.getFps()
                    , cameraCaptureConfiguration.getRotation()
                    , cameraCaptureConfiguration.isCameraFront());
        } else {
            startCapture(Constants.WIDTH_DEFAULT, Constants.HEIGHT_DEFAULT
                    , Constants.FPS_DEFAULT, -1, Constants.IS_CAMERA_FRONT_DEFAULT);
        }
    }

    private void setRemoteRenderer(SurfaceViewRenderer remoteRenderer) {
        //初始化
        initSurfaceViewRenderer(remoteRenderer);
        this.remoteRenderer = remoteRenderer;
        if (remoteStream != null && !hasRemoteAttached) {
            remoteStream.attach(remoteRenderer);
        }
    }

    public void leave() {
        KLog.e("stop " + peerId);
        hasRemoteAttached = false;
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
        if (localRenderer != null) {
            localRenderer.release();
            localRenderer = null;
        }
        if (remoteRenderer != null) {
            remoteRenderer.release();
            remoteRenderer = null;
        }
        if (remoteStream != null) {
            remoteStream = null;
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
    private void startCapture(int width, int height, int framerate, int rotation, boolean isCameraFront) {
        owtVideoCapturer = OwtVideoCapturer.create(width, height, framerate, true,
                isCameraFront);
        localStream = new LocalStream(owtVideoCapturer,
                new MediaConstraints.AudioTrackConstraints(),
                rotation == -1 ? null : new ULinkVideoProcessor(rotation));
        //投影
        localStream.attach(localRenderer);
        //发布
        publish(peerId);
    }

    public void switchCamera() {
        owtVideoCapturer.switchCamera();
    }

    /**
     * 初始化 surfaceViewRenderer
     *
     * @param surfaceViewRenderer
     */
    private void initSurfaceViewRenderer(SurfaceViewRenderer surfaceViewRenderer) {
        RCHECK(rootEglBase);
        surfaceViewRenderer.init(rootEglBase.getEglBaseContext()
                , null);
    }

    /**
     * 设置音响静音
     *
     * @param mute 静音
     */
    public void setSpeakerMute(boolean mute) {
        RCHECK(audioDeviceModule);
        audioDeviceModule.setSpeakerMute(mute);
    }

    /**
     * 设置麦克风静音
     *
     * @param mute 静音
     */
    public void setMicrophoneMute(boolean mute) {
        RCHECK(audioDeviceModule);
        audioDeviceModule.setMicrophoneMute(mute);
    }
}
