package com.lunax.telegramBot.utils;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class IpfsUtils {
    private static final Logger log = LoggerFactory.getLogger(IpfsUtils.class);
    private static IPFS ipfsInstance;
    private static final Map<String, String> loadedFiles = new HashMap<>();
    private static final String IPFS_GATEWAY = "https://ipfs.io/ipfs/";
    private static final String IPNS_GATEWAY = "https://ipfs.io/ipns/";

    private static String appIpnsKey = null;

    public static void initializeIpfs() {

        ipfsInstance = new IPFS("/ip4/127.0.0.1/tcp/5001");
        appIpnsKey = initializeIpfsKey("Offical lunax bot IPFS");
        log.info("IPNS key: {}", appIpnsKey);
        log.info("IPFS node initialized");
    }

    private static String initializeIpfsKey(String appName) {
        try {
            if (appIpnsKey != null) {
                log.info("App IPNS already initialized");
                return IPNS_GATEWAY + appIpnsKey;
            }

            // Create initial app metadata
            String metadata = String.format(
                    "{\n" +
                            "  \"appName\": \"%s\",\n" +
                            "}",
                    appName);

            // Add metadata to IPFS
            NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper("app-metadata.json",
                    metadata.getBytes(StandardCharsets.UTF_8));

            MerkleNode addResult = ipfsInstance.add(file).get(0);
            String metadataHash = addResult.hash.toString();

            // Generate a new key for the app
            String keyName = appName.toLowerCase().replace(" ", "-") + "-key";
            KeyInfo keyGen = ipfsInstance.key.gen(keyName, Optional.of("rsa"), Optional.of("2048"));
            String newKeyId = keyGen.id.toString();

            // Publish using the new key
            @SuppressWarnings("unchecked") // Suppress the unchecked warning
            Map<String, Object> pubResult = (Map<String, Object>) ipfsInstance.name
                    .publish(Multihash.fromBase58(metadataHash), Optional.of(newKeyId));
            appIpnsKey = (String) pubResult.get("Name");

            log.info("App IPNS initialized with key: {}", appIpnsKey);
            log.info("Initial metadata hash: {}", metadataHash);

            // Ensure appIpnsKey is not null before returning
            if (appIpnsKey == null) {
                log.error("Failed to initialize app IPNS key.");
                return null; // or handle the error as needed
            }

            return IPNS_GATEWAY + appIpnsKey;

        } catch (IOException e) {
            log.error("Error initializing app IPNS: {}", e.getMessage());
            return null;
        }
    }

    public static String addFile(String filePath) {
        try {
            NamedStreamable.FileWrapper fileWrapper = new NamedStreamable.FileWrapper(new File(filePath));
            MerkleNode addResult = ipfsInstance.add(fileWrapper).get(0);
            String hash = addResult.hash.toString();

            // Pin the file locally so the gateway can fetch it reliably
            ipfsInstance.pin.add(Multihash.fromBase58(hash));

            loadedFiles.put(filePath, hash);

            // Warm up the IPFS gateway cache for this file three times
            for (int i = 0; i < 3; i++) {
                warmUpCache(hash);
            }

            return hash;
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return null;
        }
    }

    public static byte[] getFile(Multihash fileHash) {
        try {
            return ipfsInstance.cat(fileHash);
        } catch (IOException e) {
            log.error("Error getting file: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns a map of all files that have been loaded to IPFS
     * 
     * @return Map of filepath to IPFS hash
     */
    public static Map<String, String> getAllFileLoaded() {
        return new HashMap<>(loadedFiles);
    }

    /**
     * Gets the HTTP URL for an IPFS hash
     * 
     * @param ipfsHash The IPFS hash
     * @return The HTTP URL
     */
    public static String getIpfsUrl(String ipfsHash) {
        return IPFS_GATEWAY + ipfsHash;
    }

    public static String addByteArray(String fileName, byte[] data) {
        try {
            NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(fileName, data);
            MerkleNode addResult = ipfsInstance.add(file).get(0);
            String hash = addResult.hash.toString();

            // Pin the byte array as well
            ipfsInstance.pin.add(Multihash.fromBase58(hash));

            loadedFiles.put(fileName, hash);

            // Warm up the IPFS gateway cache for this byte array three times
            for (int i = 0; i < 3; i++) {
                warmUpCache(hash);
            }

            return hash;
        } catch (IOException e) {
            log.error("Error adding byte array to IPFS: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Warm up the IPFS gateway cache by sending an HTTP GET to the IPFS URL.
     *
     * @param hash The IPFS hash of the file.
     */
    private static void warmUpCache(String hash) {
        String urlString = IPFS_GATEWAY + hash;
        try {
            // Create a URI first and then convert it to a URL
            URL url = new URI(urlString).toURL();
            
            // Check if the URL is for the IPFS gateway
            if (url.getHost().equals("ipfs.io")) {
                // Set up a trust manager that accepts all certificates
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
                };

                // Install the all-trusting trust manager
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            }

            // Open connection
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            // Disable hostname verification for ipfs.io
            if (url.getHost().equals("ipfs.io")) {
                connection.setHostnameVerifier((hostname, session) -> true);
            }
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            log.info("Warmed up IPFS cache for hash {} with response code: {}", hash, responseCode);
            connection.disconnect();
        } catch (Exception e) { // Catch both IOException and URISyntaxException
            log.error("Error warming up IPFS cache for hash {}: {}", hash, e.getMessage());
        }
    }
}
