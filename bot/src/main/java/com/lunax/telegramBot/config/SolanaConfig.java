package com.lunax.telegramBot.config;

import org.p2p.solanaj.rpc.Cluster;
import org.p2p.solanaj.rpc.RpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SolanaConfig {
    
    @Value("${solana.cluster}")
    private String solanaCluster;
    
    @Value("${solana.cluster.url}")
    private String clusterUrl;
    
    @Bean
    public RpcClient rpcClient() {
        return new RpcClient(clusterUrl);
    }
    
    @Bean
    public Cluster getCluster() {
        return switch (solanaCluster.toUpperCase()) {
            case "MAINNET" -> Cluster.MAINNET;
            case "TESTNET" -> Cluster.TESTNET;
            default -> Cluster.DEVNET;
        };
    }
    
    @Bean
    public String getClusterUrl() {
        return clusterUrl;
    }
}
