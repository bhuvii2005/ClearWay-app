package com.cleanroute.api.config;

import com.cleanroute.api.entity.User;
import com.cleanroute.api.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
public class DataInitializer {

    private static final String MOCK_USER_ID = "00000000-0000-0000-0000-000000000001";

    @Bean
    public CommandLineRunner initDatabase(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                // Check if user exists First
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, UUID.fromString(MOCK_USER_ID));
                
                if (count == null || count == 0) {
                    jdbcTemplate.update(
                        "INSERT INTO users (id, name, email, phone, created_at, last_active, whatsapp_opt_in) " +
                        "VALUES (?, 'Demo User', 'demo@cleanroute.com', '+1234567890', NOW(), NOW(), false)",
                        UUID.fromString(MOCK_USER_ID)
                    );
                    System.out.println("Initialized mock user for frontend testing via Native SQL: " + MOCK_USER_ID);
                } else {
                    System.out.println("Mock user already exists in database.");
                }
            } catch (Exception e) {
                 System.out.println("DataInitializer Error: " + e.getMessage());
            }
        };
    }
}
