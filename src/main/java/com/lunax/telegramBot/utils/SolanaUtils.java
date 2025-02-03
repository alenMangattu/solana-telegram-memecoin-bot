package com.lunax.telegramBot.utils;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import java.util.HashMap;
import java.util.Map;

public class SolanaUtils {
    
    /**
     * Creates a new Solana wallet and returns its public key and private key
     * @return Map containing the public key (as Base58 string) and private key (as Base58 string)
     */
    public static Map<String, String> createNewWallet() {
        Account account = new Account();
        Map<String, String> walletInfo = new HashMap<>();
        
        // Get the public key in Base58 format
        walletInfo.put("publicKey", account.getPublicKey().toString());
        
        // Get the private key and encode it in Base58
        byte[] privateKey = account.getSecretKey();
        String encodedPrivateKeyBase58 = Base58.encode(privateKey);
        walletInfo.put("privateKey", encodedPrivateKeyBase58);
        
        return walletInfo;
    }
}