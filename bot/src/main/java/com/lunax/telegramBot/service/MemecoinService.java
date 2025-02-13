package com.lunax.telegramBot.service;

import com.lunax.telegramBot.model.db.Memecoin;
import com.lunax.telegramBot.model.repository.MemecoinRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MemecoinService {

    @Autowired
    private MemecoinRepo memecoinRepo;

    public Memecoin addMemecoin(Memecoin memecoin) {
        return memecoinRepo.save(memecoin);
    }
} 