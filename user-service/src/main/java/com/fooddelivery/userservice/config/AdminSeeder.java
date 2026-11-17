package com.fooddelivery.userservice.config;

import com.fooddelivery.userservice.model.Role;
import com.fooddelivery.userservice.model.User;
import com.fooddelivery.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Cria um ADMIN na primeira execução (ninguém pode se cadastrar como ADMIN pela API). */
@Configuration
public class AdminSeeder {

    @Bean
    CommandLineRunner seedAdmin(UserRepository repo, PasswordEncoder encoder,
                                @Value("${app.admin.name}") String name,
                                @Value("${app.admin.email}") String email,
                                @Value("${app.admin.password}") String password) {
        return args -> {
            if (!repo.existsByEmail(email)) {
                User admin = new User();
                admin.setName(name);
                admin.setEmail(email);
                admin.setPasswordHash(encoder.encode(password));
                admin.setRole(Role.ADMIN);
                repo.save(admin);
            }
        };
    }
}
