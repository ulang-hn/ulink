package com.ulang.libulink.common;

public final class CameraCaptureConfiguration {
    private int width;
    private int height;
    private int fps;
    private boolean isCameraFront;

    public CameraCaptureConfiguration(int width, int height, int fps, boolean isCameraFront) {
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.isCameraFront = isCameraFront;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public boolean isCameraFront() {
        return isCameraFront;
    }

    public void setCameraFront(boolean cameraFront) {
        isCameraFront = cameraFront;
    }
}
