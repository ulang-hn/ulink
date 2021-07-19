package com.ulang.libulink.common;

import org.webrtc.VideoFrame;
import org.webrtc.VideoProcessor;
import org.webrtc.VideoSink;

public class ULinkVideoProcessor implements VideoProcessor {
    private VideoSink videoSink;
    private final int rotation;

    public ULinkVideoProcessor(int rotation) {
        this.rotation = rotation;
    }

    @Override
    public void setSink(VideoSink videoSink) {
        this.videoSink = videoSink;
    }

    @Override
    public void onCapturerStarted(boolean b) {

    }

    @Override
    public void onCapturerStopped() {

    }

    @Override
    public void onFrameCaptured(VideoFrame videoFrame) {
        VideoFrame frame = new VideoFrame(videoFrame.getBuffer(), rotation,
                videoFrame.getTimestampNs());
        videoSink.onFrame(frame);
    }
}
