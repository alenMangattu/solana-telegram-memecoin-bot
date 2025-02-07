package com.lunax.telegramBot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.lunax.telegramBot.utils.IpfsUtils;

@SpringBootApplication
public class TelegramBotApplication {
	public static void main(String[] args) {
		SpringApplication.run(TelegramBotApplication.class, args);
		IpfsUtils.initializeIpfs();
	}
}