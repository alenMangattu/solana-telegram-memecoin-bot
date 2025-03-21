package com.lunax.telegramBot.model.db;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "copy_trading")
@NoArgsConstructor
@AllArgsConstructor
public class CopyTrading {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_wallet", nullable = false)
    private Wallet sourceWallet;

    @Column(name = "target_wallet", nullable = false)
    private String targetAddress;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "amount_multiplier", nullable = false)
    private double amountMultiplier = 1.0; // Default to 1.0 (same amount)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getTargetWalletAddress() {
        return targetAddress;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
