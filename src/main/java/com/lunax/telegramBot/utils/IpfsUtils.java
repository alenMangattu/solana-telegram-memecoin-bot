package com.lunax.telegramBot.utils;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.ipfs.api.IPFS;
import io.ipfs.api.KeyInfo;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;

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

            return IPNS_GATEWAY + appIpnsKey;

        } catch (IOException e) {
            log.error("Error initializing app IPNS: {}", e.getMessage());
            return null;
        }
    }

    public static String addFile(String filePath) {
        try {
            NamedStreamable.FileWrapper file = new NamedStreamable.FileWrapper(new File(filePath));
            MerkleNode addResult = ipfsInstance.add(file).get(0);
            String hash = addResult.hash.toString();
            loadedFiles.put(filePath, hash);
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
            loadedFiles.put(fileName, hash);
            return hash;
        } catch (IOException e) {
            log.error("Error adding byte array to IPFS: {}", e.getMessage());
            return null;
        }
    }
}
