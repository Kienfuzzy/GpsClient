package com.gpsclient; // Package declaration for the GPS client application

import org.eclipse.paho.client.mqttv3.*; // Import MQTT library for client-server communication
import javax.net.ssl.*; // Import SSL libraries for TLS/SSL connections
import java.io.FileInputStream; // For reading the CA certificate file
import java.io.InputStream; // Input stream for reading certificates
import java.security.KeyStore; // KeyStore to manage certificates
import java.security.cert.Certificate; // Handling certificates
import java.security.cert.CertificateFactory; // Factory to generate certificates in X.509 format
import java.util.Collection; // To manage a collection of certificates
import java.util.Random; // For generating random GPS coordinates
import java.util.UUID; // For generating unique client IDs

/**
 * GpsClient:
 * This class connects to an MQTT broker using TLS/SSL and publishes simulated GPS coordinates.
 * Coordinates are published every 10 seconds in the format: ClientID,Latitude,Longitude.
 */
public class GpsClient {

    private static final String BROKER_URL = "ssl://broker.emqx.io:8883"; // Secure MQTT broker URL using SSL/TLS
    private static final String TOPIC = "gps/clients"; // Topic where GPS data will be published

    // Latitude and longitude boundaries for generating random coordinates
    private static final double MIN_LAT = 37.0;
    private static final double MAX_LAT = 37.8;
    private static final double MIN_LON = -122.5;
    private static final double MAX_LON = -121.7;

    public static void main(String[] args) {
        try {
            // Load the CA certificate to establish trust with the MQTT broker
            InputStream certInput = new FileInputStream("src/main/resources/server_ca.crt");
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(certInput);

            // Create a KeyStore and load the certificates into it
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null); // Initialize an empty KeyStore

            int index = 0;
            for (Certificate cert : certificates) {
                keyStore.setCertificateEntry("server_ca_" + index++, cert); // Add each certificate with a unique alias
            }

            // Initialize TrustManagerFactory with the loaded KeyStore
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            // Create SSLContext with the TrustManager for establishing SSL connections
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory(); // Obtain SSLSocketFactory for secure connection

            // Generate a unique client ID
            String clientId = "Client-" + UUID.randomUUID().toString().substring(0, 5);

            // Initialize the MQTT client with the broker URL and client ID
            MqttClient mqttClient = new MqttClient(BROKER_URL, clientId);

            // Configure connection options
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true); // Ensures no previous session is used
            options.setSocketFactory(sslSocketFactory); // Use SSL socket factory

            System.out.println("Connecting to MQTT broker at " + BROKER_URL + " with Client ID: " + clientId + "...");

            try {
                mqttClient.connect(options); // Connect to the MQTT broker
                System.out.println("Connected successfully as " + clientId);
            } catch (MqttException e) {
                System.out.println("Failed to connect: " + e.getMessage());
                return;
            }

            Random random = new Random();

            // Continuously publish random GPS coordinates every 10 seconds
            while (true) {
                double latitude = MIN_LAT + (MAX_LAT - MIN_LAT) * random.nextDouble();
                double longitude = MIN_LON + (MAX_LON - MIN_LON) * random.nextDouble();

                String payload = String.format("%s,%.6f,%.6f", clientId, latitude, longitude);
                
                try {
                    mqttClient.publish(TOPIC, new MqttMessage(payload.getBytes()));
                    System.out.println("Published: " + payload);
                } catch (MqttException e) {
                    System.out.println("⚠️ Failed to publish: " + e.getMessage());
                }

                Thread.sleep(10000); // Wait 10 seconds before next publish
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Make sure the CA certificate is valid and matches the broker's certificate chain.");
            System.out.println("If the certificate was downloaded using wget, ensure it was fully downloaded and not corrupted.");
            e.printStackTrace();
        }
    }
}
