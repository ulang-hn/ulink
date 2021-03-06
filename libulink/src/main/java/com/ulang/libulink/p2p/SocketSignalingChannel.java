/*
 * Copyright (C) 2018 Intel Corporation
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ulang.libulink.p2p;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter.Listener;
import owt.base.ActionCallback;
import owt.base.Const;
import owt.base.OwtError;
import owt.p2p.OwtP2PError;
import owt.p2p.SignalingChannelInterface;

import static owt.p2p.OwtP2PError.P2P_CLIENT_ILLEGAL_ARGUMENT;
import static owt.p2p.OwtP2PError.P2P_CONN_SERVER_UNKNOWN;

/**
 * Socket.IO implementation of P2P signaling channel.
 */
class SocketSignalingChannel implements SignalingChannelInterface {
    private static final String TAG = "OWT-SocketClient";
    private final String CLIENT_CHAT_TYPE = "owt-message";
    private final String CLIENT_AUTHENTICATE_TYPE = "authentication";
    private final String FORCE_DISCONNECT = "server-disconnect";
    private final String CLIENT_TYPE = "&clientType=";
    private final String CLIENT_TYPE_VALUE = "Android";
    private final String CLIENT_VERSION = "&clientVersion=";
    private final String CLIENT_VERSION_VALUE = Const.CLIENT_VERSION;

    private final int MAX_RECONNECT_ATTEMPTS = 5;
    private int reconnectAttempts = 0;
    private Socket socketIOClient;
    private List<SignalingChannelObserver> signalingChannelObservers;
    private ActionCallback<String> connectCallback;

    private String userInfo = null;

    // Socket.IO events.
    private Listener onConnectedCallback = args -> {
        JSONObject loginObject = null;
        try {
            loginObject = new JSONObject(userInfo);
            String token = URLEncoder.encode(loginObject.getString("token"), "UTF-8");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("token", token);
            socketIOClient.emit(CLIENT_AUTHENTICATE_TYPE, jsonObject, (Ack) authenticateArgs -> {
                if (authenticateArgs == null || authenticateArgs.length != 1) {
                    if (connectCallback != null) {
                        connectCallback.onFailure(new OwtError("authenticate fail."));
                    }
                } else {
                    if (connectCallback != null) {
                        try {
                            String authenticateToken = authenticateArgs[0].toString();
                            JSONObject authenticateJson = new JSONObject(authenticateToken);
                            String uid = authenticateJson.getString("uid");
                            if (uid != null) {
                                connectCallback.onSuccess(authenticateToken);
                            } else {
                                connectCallback.onFailure(new OwtError("authenticate fail."));
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    };

    private Listener onConnectErrorCallback = args -> {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            if (connectCallback != null) {
                connectCallback.onFailure(
                        new OwtError(P2P_CONN_SERVER_UNKNOWN.value, "connect failed"));
                connectCallback = null;
            } else {
                for (SignalingChannelObserver observer : signalingChannelObservers) {
                    observer.onServerDisconnected();
                }
            }
            reconnectAttempts = 0;
        }
    };

    private Listener onErrorCallback = args -> {
        if (connectCallback != null) {
            Pattern pattern = Pattern.compile("[0-9]*");
            if (pattern.matcher(args[0].toString()).matches()) {
                connectCallback.onFailure(
                        new OwtError(OwtP2PError.get(Integer.parseInt((String) args[0])).value,
                                "Server error"));
            } else {
                connectCallback.onFailure(new OwtError(args[0].toString()));
            }
        }
    };

    private Listener onReconnectingCallback = args -> {
        reconnectAttempts++;
    };

    private Listener onDisconnectCallback = args -> {
        for (SignalingChannelObserver observer : signalingChannelObservers) {
            observer.onServerDisconnected();
        }
    };

    // P2P server events.
    private Listener onForceDisconnectCallback = args -> {
        if (socketIOClient != null) {
            socketIOClient.on(Socket.EVENT_DISCONNECT, onDisconnectCallback);
            socketIOClient.io().reconnection(false);
        }
    };

    private Listener onMessageCallback = args -> {
        JSONObject argumentJsonObject = (JSONObject) args[0];
        for (SignalingChannelObserver observer : signalingChannelObservers) {
            try {
                observer.onMessage(argumentJsonObject.getString("from"),
                        argumentJsonObject.getString("data"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    SocketSignalingChannel() {
        this.signalingChannelObservers = new ArrayList<>();
    }

    @Override
    public void addObserver(SignalingChannelObserver observer) {
        this.signalingChannelObservers.add(observer);
    }

    @Override
    public void removeObserver(SignalingChannelObserver observer) {
        this.signalingChannelObservers.remove(observer);
    }

    @Override
    public void connect(String userInfo, ActionCallback<String> callback) {
        JSONObject loginObject;
        String url;
        this.userInfo = userInfo;
        try {
            connectCallback = callback;
            loginObject = new JSONObject(userInfo);
            url = loginObject.getString("host");
            if (!isValid(url)) {
                callback.onFailure(new OwtError(P2P_CLIENT_ILLEGAL_ARGUMENT.value, "Invalid URL"));
                return;
            }
            IO.Options opt = new IO.Options();
            opt.forceNew = true;
            opt.reconnection = true;
            opt.reconnectionAttempts = MAX_RECONNECT_ATTEMPTS;
            //opt.transports=new String[]{WebSocket.NAME};
            if (socketIOClient != null) {
                Log.d(TAG, "stop reconnecting the former url");
                socketIOClient.disconnect();
            }
            socketIOClient = IO.socket(url, opt);

            socketIOClient.on(Socket.EVENT_CONNECT_ERROR, onConnectErrorCallback)
                    .on(Socket.EVENT_ERROR, onErrorCallback)
                    .on(Socket.EVENT_RECONNECTING, onReconnectingCallback)
                    .on(CLIENT_CHAT_TYPE, onMessageCallback)
                    .on(FORCE_DISCONNECT, onForceDisconnectCallback)
                    .on(Socket.EVENT_CONNECT, onConnectedCallback);

            socketIOClient.connect();

        } catch (JSONException e) {
            if (callback != null) {
                callback.onFailure(new OwtError(P2P_CLIENT_ILLEGAL_ARGUMENT.value, e.getMessage()));
            }
        } catch (URISyntaxException e) {
            if (callback != null) {
                callback.onFailure(new OwtError(P2P_CLIENT_ILLEGAL_ARGUMENT.value, e.getMessage()));
            }
        }
    }

    private boolean isValid(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getPort() <= 65535;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    @Override
    public void disconnect() {
        if (socketIOClient != null) {
            Log.d(TAG, "Socket IO Disconnect.");
            socketIOClient.on(Socket.EVENT_DISCONNECT, onDisconnectCallback);
            socketIOClient.disconnect();
            socketIOClient = null;
        }
    }

    @Override
    public void sendMessage(String peerId, String message, final ActionCallback<Void> callback) {
        if (socketIOClient == null) {
            Log.d(TAG, "socketIOClient is not established.");
            return;
        }
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("to", peerId);
            jsonObject.put("data", message);
            socketIOClient.emit(CLIENT_CHAT_TYPE, jsonObject, (Ack) args -> {
                if (args == null || args.length != 0 && args[0] != null) {
                    if (callback != null) {
                        callback.onFailure(new OwtError("Failed to send message."));
                    }
                } else {
                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
