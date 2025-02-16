package com.lunax.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.lunax.telegramBot.model.db.Memecoin;
import com.lunax.telegramBot.model.repository.MemecoinRepo;

@Service
public class MemecoinDbService {
    @Autowired
    private MemecoinRepo memecoinRepo;

    public Memecoin addMemecoin(Memecoin memecoin) {
        return memecoinRepo.save(memecoin);
    }
} 