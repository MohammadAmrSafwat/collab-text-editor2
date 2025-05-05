package org.example.client.network;

import org.example.client.model.ClientMessage;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ServerConnection {
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private String sessionId;
    private String userId;
public void connect(String serverUrl){
    StandardWebSocketClient client = new StandardWebSocketClient();
    stompClient = new WebSocketStompClient(client);
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());

    // 2. Build WebSocket URL
    String url = serverUrl.replace("http", "ws") + "/ws-collab/websocket";
    StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            stompSession = session;
            System.out.println("Successfully connected to WebSocket server");
        }
    };
    stompClient.connect(url, sessionHandler).addCallback(
            new ListenableFutureCallback<StompSession>() {
                @Override
                public void onSuccess(StompSession session) {
                    System.out.println("WebSocket connection established");
                }

                @Override
                public void onFailure(Throwable ex) {
                    System.err.println("Connection failed: " + ex.getMessage());
                }
            }
    );
}



    public void connect(String serverUrl, String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;

        // 1. Create WebSocket client
        StandardWebSocketClient client = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // 2. Build WebSocket URL
        String url = serverUrl.replace("http", "ws") + "/ws-collab/websocket";
        System.out.println(url);

        // 3. Create session handler
        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                stompSession = session;
                System.out.println("Successfully connected to WebSocket server");

                // Subscribe to document updates
                session.subscribe("/topic/session." + sessionId, new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        messageQueue.add((String) payload);
                    }
                });
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("Transport error: " + exception.getMessage());
            }
        };

        // 4. Connect with callback
        stompClient.connect(url, sessionHandler).addCallback(
                new ListenableFutureCallback<StompSession>() {
                    @Override
                    public void onSuccess(StompSession session) {
                        System.out.println("WebSocket connection established");
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        System.err.println("Connection failed: " + ex.getMessage());
                    }
                }
        );
    }

    public void sendOperation(String type, Object data) {
        if (stompSession != null && stompSession.isConnected()) {
            ClientMessage message = new ClientMessage(type, sessionId, userId, data);
            stompSession.send("/app/session." + sessionId + "/operation", message);
        }
    }

    public String receiveMessage() throws InterruptedException {
        return messageQueue.poll(5, TimeUnit.SECONDS);
    }

    public void disconnect() {
        if (stompSession != null) {
            stompSession.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }
}