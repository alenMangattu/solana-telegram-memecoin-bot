package com.lunax.telegramBot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

@Component
public class StartupConfig {
    
    @Value("${wallets.path}")
    private String walletsPath;
    
    @Value("${encryption.keys.path}")
    private String encryptionKeysPath;
    
    @PostConstruct
    public void init() throws IOException {
        // Create directories with proper permissions
        Files.createDirectories(Paths.get(walletsPath));
        Files.createDirectories(Paths.get(encryptionKeysPath));
        
        // Set directory permissions
        Files.setPosixFilePermissions(Paths.get(walletsPath), 
            PosixFilePermissions.fromString("rwx------"));
        Files.setPosixFilePermissions(Paths.get(encryptionKeysPath), 
            PosixFilePermissions.fromString("rwx------"));
    }
} 