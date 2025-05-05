module org.example.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires spring.messaging;
    requires spring.websocket;
    requires org.apache.tomcat.embed.websocket;
    requires spring.core;
    requires spring.web;
    requires java.net.http;


    opens org.example.client to javafx.fxml;
    exports org.example.client;
}