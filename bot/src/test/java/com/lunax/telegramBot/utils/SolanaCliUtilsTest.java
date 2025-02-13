package com.lunax.telegramBot.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

public class SolanaCliUtilsTest {

    private SolanaCliUtils solanaCliUtils;
    private String clusterUrl = "https://api.devnet.solana.com";
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        solanaCliUtils = new SolanaCliUtils("https://api.devnet.solana.com");
        ReflectionTestUtils.setField(solanaCliUtils, "clusterUrl", clusterUrl);
        ReflectionTestUtils.setField(solanaCliUtils, "solanaHomeDir", tempDir.toString());
    }

    @Test
    void createMemecoin_ShouldCreateTokenSuccessfully() throws Exception {
        // Arrange
        String walletKeyPath = tempDir.resolve("test-keypair.json").toString();
        String name = "Test Token";
        String symbol = "TEST";
        int decimals = 9;
        String metadataJson = "{\"name\":\"Test Token\",\"symbol\":\"TEST\"}";
        
        // Mock successful process execution
        Process processMock = mock(Process.class);
        when(processMock.getInputStream()).thenReturn(
            new ByteArrayInputStream("Creating token tokenAddress123\n".getBytes()));
        when(processMock.waitFor(anyLong(), any())).thenReturn(true);
        when(processMock.exitValue()).thenReturn(0);

        ProcessBuilder processBuilderMock = mock(ProcessBuilder.class);
        when(processBuilderMock.start()).thenReturn(processMock);

        // Use mockStatic for ProcessBuilder if possible in your test environment
        // Otherwise, this test might need to be restructured

        // Act
        String result = solanaCliUtils.createMemecoin(walletKeyPath, name, symbol, decimals, metadataJson);

        // Assert
        assertEquals("tokenAddress123", result);
    }

    @Test
    void saveWalletKeypair_ShouldSaveAndReturnPath() throws Exception {
        // Arrange
        String privateKey = "testPrivateKey123";

        // Act
        String result = solanaCliUtils.saveWalletKeypair(privateKey);

        // Assert
        assertTrue(Files.exists(Path.of(result)));
        assertEquals(privateKey, Files.readString(Path.of(result)));
    }

    @Test
    void deleteWalletKeypair_ShouldDeleteFile() throws Exception {
        // Arrange
        Path keypairPath = tempDir.resolve("test-keypair.json");
        Files.writeString(keypairPath, "test content");

        // Act
        solanaCliUtils.deleteWalletKeypair(keypairPath.toString());

        // Assert
        assertFalse(Files.exists(keypairPath));
    }

    @Test
    void mintTokens_ShouldExecuteSuccessfully() throws Exception {
        // Arrange
        String walletKeyPath = tempDir.resolve("test-keypair.json").toString();
        String tokenAddress = "tokenAddress123";
        String tokenAccount = "tokenAccount123";
        double amount = 1000000L;

        Process processMock = mock(Process.class);
        when(processMock.waitFor(anyLong(), any())).thenReturn(true);
        when(processMock.exitValue()).thenReturn(0);

        ProcessBuilder processBuilderMock = mock(ProcessBuilder.class);
        when(processBuilderMock.start()).thenReturn(processMock);

        // Act & Assert
        assertDoesNotThrow(() -> 
            solanaCliUtils.mintTokens(walletKeyPath, tokenAddress, amount));
    }

    @Test
    void revokeMintAuthority_ShouldExecuteSuccessfully() throws Exception {
        // Arrange
        String walletKeyPath = tempDir.resolve("test-keypair.json").toString();
        String tokenAddress = "tokenAddress123";

        Process processMock = mock(Process.class);
        when(processMock.waitFor(anyLong(), any())).thenReturn(true);
        when(processMock.exitValue()).thenReturn(0);

        ProcessBuilder processBuilderMock = mock(ProcessBuilder.class);
        when(processBuilderMock.start()).thenReturn(processMock);

        // Act & Assert
        assertDoesNotThrow(() -> 
            solanaCliUtils.revokeMintAuthority(walletKeyPath, tokenAddress));
    }
} 