package com.lunax.telegramBot.model.dto;

import com.fasterxml.jackson.databind.JsonNode;

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
public class TradingAutomationDTO {
    private String strategyName;
    private JsonNode parameters;
    private boolean enabled;
}