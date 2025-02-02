package com.lunax.telegramBot.model.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WalletDTO {
    private UUID walletId;
    private String walletAddress;
    private String walletLabel;
}
