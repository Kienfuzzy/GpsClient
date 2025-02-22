package com.gpsclient; // Package declaration for the GPS dashboard application

import org.eclipse.paho.client.mqttv3.*; // Import MQTT library for client-server communication
import javax.net.ssl.*; // Import SSL libraries for TLS/SSL connections
import java.io.FileInputStream; // For reading the CA certificate file
import java.io.InputStream; // Input stream for certificate reading
import java.security.KeyStore; // KeyStore to store certificates
import java.security.cert.Certificate; // Certificate handling
import java.security.cert.CertificateFactory; // Certificate factory for X.509 format
import java.util.Collection; // Collection for multiple certificates

/**
 * GpsDashboard:
 * This class represents a console-based MQTT dashboard that:
 * - Connects to an MQTT broker using TLS/SSL for secure communication.
 * - Subscribes to a topic to receive GPS coordinates from clients.
 * - Displays received GPS coordinates in the console in real-time.
 */
public class GpsDashboard {

    private static final String BROKER_URL = "ssl://broker.emqx.io:8883"; // Secure MQTT broker URL using SSL/TLS protocol
    private static final String TOPIC = "gps/clients"; // Topic to subscribe to for receiving GPS data

    public static void main(String[] args) {
        try {
            // Load the server-side CA certificate from the file
            InputStream certInput = new FileInputStream("src/main/resources/server_ca.crt");
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509"); // Create a certificate factory for X.509 certificates
            Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(certInput); // Load certificates from the input stream

            // Create a KeyStore to store the loaded certificates
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null); // Initialize an empty KeyStore

            int index = 0;
            for (Certificate cert : certificates) {
                keyStore.setCertificateEntry("server_ca_" + index++, cert); // Add certificates to the KeyStore with unique aliases
            }

            // Initialize a TrustManagerFactory using the loaded KeyStore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore); // Set the KeyStore into the TrustManagerFactory

            // Create an SSLContext using the initialized TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null); // Initialize SSLContext with the TrustManager

            // Obtain an SSLSocketFactory from the SSLContext for secure connections
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            // Initialize the MQTT client with the broker URL and a unique client ID
            String clientId = "GpsDashboard-" + System.currentTimeMillis();
            MqttClient mqttClient = new MqttClient(BROKER_URL, clientId);

            // Configure MQTT connection options
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true); // Start a new session without saving previous state
            options.setSocketFactory(sslSocketFactory); // Use the SSLSocketFactory for secure connection

            System.out.println("Connecting to the MQTT broker at " + BROKER_URL + "...");

            try {
                // Connect to the MQTT broker with the configured options
                mqttClient.connect(options);
                System.out.println("Successfully connected to the MQTT broker with Client ID: " + clientId);

                // Set a callback to handle incoming messages and connection events
                mqttClient.setCallback(new MqttCallback() {

                    @Override
                    public void connectionLost(Throwable cause) {
                        System.out.println("Connection lost: " + cause.getMessage()); // Handle lost connections
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        // Process received messages and display the topic and message content
                        System.out.println("Received message on topic: " + topic);
                        System.out.println("QoS: " + message.getQos());
                        System.out.println("Content: " + new String(message.getPayload()));
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // Not applicable since this client only subscribes and does not publish messages
                    }
                });

                // Subscribe to the specified topic with QoS 1 for reliable message delivery
                int qos = 1;
                mqttClient.subscribe(TOPIC, qos);
                System.out.println("Subscribed to topic: " + TOPIC + ". Listening for incoming GPS coordinates...");

                // Keep the application running to continue receiving messages
                while (true) {
                    Thread.sleep(1000); // Prevent the application from exiting by sleeping the main thread
                }

            } catch (MqttException e) {
                System.out.println("Failed to connect or subscribe: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
