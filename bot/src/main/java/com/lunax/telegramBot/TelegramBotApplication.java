package com.lunax.telegramBot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.ComponentScan;

import com.lunax.telegramBot.utils.IpfsUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@EnableScheduling
@RestController
@ComponentScan(basePackages = {
    "com.lunax.telegramBot.service",  // Load services first 
    "com.lunax.telegramBot.config",   // Then configs
    "com.lunax.telegramBot.model",    // Then models
    "com.lunax.telegramBot.component", // Then components
    "com.lunax.telegramBot.controller", // Then controllers
    "com.lunax.telegramBot.handler",  // Then handlers
    "com.lunax.telegramBot.utils"     // Then utilities
})
public class TelegramBotApplication {
	public static void main(String[] args) {
		SpringApplication.run(TelegramBotApplication.class, args);
		IpfsUtils.initializeIpfs();
	}

	@GetMapping("/")
	public String hello() {
		return "Hello World";
	}
}