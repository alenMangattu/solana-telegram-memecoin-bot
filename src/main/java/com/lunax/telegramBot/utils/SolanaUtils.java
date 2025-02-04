package com.lunax.telegramBot.utils;

import java.util.HashMap;
import java.util.Map;

import org.bitcoinj.core.Base58;
import org.p2p.solanaj.core.Account;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;

public class SolanaUtils {
    // Using a reliable public RPC endpoint
    private static final RpcClient client = new RpcClient(Cluster.DEVNET);
    
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
        System.out.println("Private key: " + encodedPrivateKeyBase58);
        walletInfo.put("privateKey", encodedPrivateKeyBase58);
        
        return walletInfo;
    }
    
    /**
     * Gets the balance of a Solana wallet address in SOL
     * @param address The wallet address in Base58 format
     * @return The balance in SOL (as a double)
     */
    public static double getBalance(String address) {
        try {
            System.out.println("Attempting to get balance for address: " + address);
            PublicKey publicKey = new PublicKey(address);
            
            try {
                long lamports = client.getApi().getBalance(publicKey);
                System.out.println("RPC Response - Lamports: " + lamports);
                double solBalance = lamports / 1000000000.0;
                System.out.println("Calculated SOL Balance: " + solBalance);
                return solBalance;
            } catch (Exception rpcError) {
                System.err.println("RPC Error: " + rpcError.getMessage());
                rpcError.printStackTrace();
                throw new RuntimeException("RPC call failed", rpcError);
            }
        } catch (Exception e) {
            System.err.println("General Error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to get balance for address: " + address, e);
        }
    }
}
