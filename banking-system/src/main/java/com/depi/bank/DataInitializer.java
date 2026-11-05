package com.depi.bank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DataInitializer implements ApplicationRunner {
    private final DataSource ds;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder enc;
    private final String seedMode;

    public DataInitializer(DataSource ds, JdbcTemplate jdbc, PasswordEncoder enc,
                           @Value("${app.seed:admin}") String seedMode) {
        this.ds = ds;
        this.jdbc = jdbc;
        this.enc = enc;
        this.seedMode = seedMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (n == null || n > 0) return;

        String mode = seedMode == null ? "admin" : seedMode.trim().toLowerCase();
        if (mode.equals("none")) {
            System.out.println(">>> TQ Bank database created EMPTY (app.seed=none).");
            return;
        }
        String file = mode.equals("buggy") ? "seed-buggy.sql" : "seed-admin.sql";
        new ResourceDatabasePopulator(new ClassPathResource(file)).execute(ds);
        jdbc.update("UPDATE users SET password_hash=? WHERE password_hash LIKE '$2a$10$7Eq%'",
                enc.encode("Test@1234"));
        if (mode.equals("buggy")) {
            System.out.println(">>> TQ Bank database seeded with DEMO DATA. Password for seeded users: Test@1234");
        } else {
            System.out.println(">>> TQ Bank database is EMPTY except one admin: admin@bank.com / Test@1234");
        }
    }
}
