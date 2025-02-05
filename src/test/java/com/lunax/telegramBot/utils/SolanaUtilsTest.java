package com.lunax.telegramBot.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.p2p.solanaj.rpc.RpcClient;

import com.lunax.telegramBot.model.db.Wallet;
import com.lunax.telegramBot.model.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
public class SolanaUtilsTest {

    @Mock
    private WalletKeyManager walletKeyManager;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private SolanaCliUtils solanaCliUtils;

    @Mock
    private RpcClient rpcClient;

    @InjectMocks
    private SolanaUtils solanaUtils;

    private UUID walletId;
    private Wallet testWallet;
    private String testPrivateKey;
    private String testKeypairPath;

    @BeforeEach
    void setUp() {
        walletId = UUID.randomUUID();
        testWallet = new Wallet();
        testWallet.setId(walletId);
        testWallet.setWalletAddress("testAddress123");
        testPrivateKey = "testPrivateKey123";
        testKeypairPath = "/tmp/test-keypair.json";
    }

    @Test
    void createNewWallet_ShouldReturnValidWalletInfo() {
        Map<String, String> walletInfo = SolanaUtils.createNewWallet();
        
        assertNotNull(walletInfo);
        assertTrue(walletInfo.containsKey("publicKey"));
        assertTrue(walletInfo.containsKey("privateKey"));
        assertNotNull(walletInfo.get("publicKey"));
        assertNotNull(walletInfo.get("privateKey"));
    }

    @Test
    void createMemecoin_ShouldCreateTokenSuccessfully() throws Exception {
        // Arrange
        String name = "Test Token";
        String symbol = "TEST";
        int decimals = 9;
        double supply = 1000000.0;
        String expectedTokenAddress = "tokenAddress123";

        when(walletRepository.findById(walletId)).thenReturn(java.util.Optional.of(testWallet));
        when(walletKeyManager.getPrivateKey(walletId)).thenReturn(testPrivateKey);
        when(solanaCliUtils.saveWalletKeypair(testPrivateKey)).thenReturn(testKeypairPath);
        when(solanaCliUtils.createMemecoin(eq(testKeypairPath), eq(name), eq(symbol), 
            eq(decimals), anyString())).thenReturn(expectedTokenAddress);

        // Act
        String result = solanaUtils.createMemecoin(walletId, name, symbol, decimals, supply);

        // Assert
        assertEquals(expectedTokenAddress, result);
        verify(solanaCliUtils).mintTokens(eq(testKeypairPath), eq(expectedTokenAddress), 
            eq(testWallet.getWalletAddress()), eq(1000000000000L));
        verify(solanaCliUtils).revokeMintAuthority(testKeypairPath, expectedTokenAddress);
        verify(solanaCliUtils).deleteWalletKeypair(testKeypairPath);
    }

    @Test
    void createMemecoin_ShouldThrowException_WhenWalletNotFound() {
        // Arrange
        when(walletRepository.findById(walletId)).thenReturn(java.util.Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> 
            solanaUtils.createMemecoin(walletId, "Test", "TEST", 9, 1000000.0));
    }

    @Test
    void getBalance_ShouldReturnCorrectBalance() {
        // This test would require mocking the RPC client response
        // Implementation depends on your RPC client structure
    }
} 