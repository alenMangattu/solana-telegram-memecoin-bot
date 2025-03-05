package com.lunax.telegramBot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lunax.telegramBot.component.Buttons.Token.TimePeriodButton;
import com.lunax.telegramBot.component.Buttons.Token.TimeFrequencyButton;
import com.lunax.telegramBot.component.Buttons.Token.BuyButton;
import com.lunax.telegramBot.component.Buttons.Token.SellButton;

@Configuration
public class ButtonConfig {

    @Bean
    public TimePeriodButton timePeriod6M() {
        return new TimePeriodButton("6M", "6M");
    }

    @Bean
    public TimePeriodButton timePeriod15D() {
        return new TimePeriodButton("15D", "15D");
    }

    @Bean
    public TimePeriodButton timePeriod1D() {
        return new TimePeriodButton("1D", "1D");
    }

    @Bean
    public TimeFrequencyButton timeFrequency15M() {
        return new TimeFrequencyButton("15m", "15m");
    }

    @Bean
    public TimeFrequencyButton timeFrequency6H() {
        return new TimeFrequencyButton("6h", "6H");
    }

    @Bean
    public TimeFrequencyButton timeFrequency1D() {
        return new TimeFrequencyButton("1d", "1D");
    }
    
    @Bean
    public BuyButton buyButton() {
        return new BuyButton();
    }
    
    @Bean
    public SellButton sellButton() {
        return new SellButton();
    }
} 