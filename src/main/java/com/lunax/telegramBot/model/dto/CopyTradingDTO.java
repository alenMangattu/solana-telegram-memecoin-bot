package com.lunax.telegramBot.model.dto;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CopyTradingDTO {
    private String sourceWallet;
    private String targetWallet;
    private boolean enabled;
}