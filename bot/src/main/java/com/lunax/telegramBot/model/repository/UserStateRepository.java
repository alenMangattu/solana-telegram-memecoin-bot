package com.lunax.telegramBot.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.lunax.telegramBot.model.db.UserState;
import com.lunax.telegramBot.model.db.User;
import java.util.Optional;

@Repository
public interface UserStateRepository extends JpaRepository<UserState, Long> {
    Optional<UserState> findByUser(User user);
}
