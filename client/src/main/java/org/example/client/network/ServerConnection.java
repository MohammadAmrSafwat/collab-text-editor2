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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles WebSocket communication with the collaboration server
 */
public class ServerConnection {
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private String sessionId;
    private String userId;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * Connects to the WebSocket server without joining a specific session
     * @param serverUrl The base server URL (e.g., "http://localhost:8080")
     */
    public void connect(String serverUrl) {
        connectInternal(serverUrl, null, null);
    }

    /**
     * Connects to the WebSocket server and joins a specific collaboration session
     * @param serverUrl The base server URL (e.g., "http://localhost:8080")
     * @param sessionId The collaboration session ID
     * @param userId The current user's ID
     */
    public void connect(String serverUrl, String sessionId, String userId) {
        this.sessionId = sessionId;
        this.userId = userId;
        connectInternal(serverUrl, sessionId, userId);
    }

    private void connectInternal(String serverUrl, String sessionId, String userId) {
        if (connected.get()) {
            disconnect();
        }

        StandardWebSocketClient client = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(client);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String url = serverUrl.replace("http", "ws") + "/ws-collab/websocket";
        System.out.println("Connecting to WebSocket at: " + url);

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                stompSession = session;
                connected.set(true);
                System.out.println("WebSocket connection established");

                if (sessionId != null) {
                    // Subscribe to document updates only if we have a session ID
                    session.subscribe("/topic/session." + sessionId, new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return String.class;
                        }

                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            if (payload != null) {
                                messageQueue.add(payload.toString());
                            }
                        }
                    });
                }
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connected.set(false);
                System.err.println("WebSocket transport error: " + exception.getMessage());
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                        StompHeaders headers, byte[] payload, Throwable exception) {
                System.err.println("WebSocket handler error: " + exception.getMessage());
            }
        };

        stompClient.connect(url, sessionHandler).addCallback(
                new ListenableFutureCallback<>() {
                    @Override
                    public void onSuccess(StompSession session) {
                        System.out.println("Successfully connected to WebSocket server");
                    }

                    @Override
                    public void onFailure(Throwable ex) {
                        connected.set(false);
                        System.err.println("Failed to connect to WebSocket: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
        );
    }

    /**
     * Sends an operation to the server
     * @param type The operation type (e.g., "INSERT", "DELETE")
     * @param data The operation payload
     * @throws IllegalStateException if not connected to a session
     */
    public void sendOperation(String type, Object data) throws IllegalStateException {
        if (!connected.get() || stompSession == null || !stompSession.isConnected()) {
            throw new IllegalStateException("Not connected to WebSocket server");
        }
        if (sessionId == null) {
            throw new IllegalStateException("No session ID specified");
        }

        try {
            ClientMessage message = new ClientMessage(type, sessionId, userId, data);
            stompSession.send("/app/session." + sessionId + "/operation", message);
        } catch (Exception e) {
            System.err.println("Failed to send operation: " + e.getMessage());
        }
    }

    /**
     * Receives a message from the server
     * @return The received message, or null if timeout occurs
     * @throws InterruptedException if interrupted while waiting
     */
    public String receiveMessage() throws InterruptedException {
        try {
            return messageQueue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
    }

    /**
     * Disconnects from the WebSocket server
     */
    public void disconnect() {
        connected.set(false);
        try {
            if (stompSession != null && stompSession.isConnected()) {
                stompSession.disconnect();
            }
        } catch (Exception e) {
            System.err.println("Error disconnecting session: " + e.getMessage());
        }
        try {
            if (stompClient != null) {
                stompClient.stop();
            }
        } catch (Exception e) {
            System.err.println("Error stopping WebSocket client: " + e.getMessage());
        }
        messageQueue.clear();
    }

    /**
     * @return true if currently connected to the server
     */
    public boolean isConnected() {
        return connected.get() && stompSession != null && stompSession.isConnected();
    }
}