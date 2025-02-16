package com.lunax.telegramBot.model.db;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "memecoins")
@NoArgsConstructor
@AllArgsConstructor
public class Memecoin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_wallet_id", nullable = false)
    private String creatorWalletId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "symbol", nullable = false, unique = true)
    private String symbol;

    @Column(name = "total_supply", nullable = false)
    private double totalSupply;

    @Column(name = "image_url")
    private String imageUrl;

    // @Column(name = "description")
    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "token_address", unique = true)
    private String tokenAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getCreatorWalletId() {
        return creatorWalletId;
    }

    public void setCreatorWalletId(String creatorWalletId) {
        this.creatorWalletId = creatorWalletId;
    }
}