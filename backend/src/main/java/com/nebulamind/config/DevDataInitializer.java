package com.nebulamind.config;

import com.nebulamind.entity.User;
import com.nebulamind.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dev profile 启动时自动创建测试用户。
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        User admin = userRepository.findByEmail("admin@nebulamind.com").orElse(null);
        if (admin != null) {
            admin.setPassword(passwordEncoder.encode("admin123"));
            userRepository.save(admin);
            log.info("Updated admin user password: admin@nebulamind.com / admin123");
        } else {
            admin = User.builder()
                    .username("admin")
                    .email("admin@nebulamind.com")
                    .password(passwordEncoder.encode("admin123"))
                    .displayName("管理员")
                    .role(User.Role.ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .build();
            userRepository.save(admin);
            log.info("Created admin user: admin@nebulamind.com / admin123");
        }

        User user = userRepository.findByEmail("user@nebulamind.com").orElse(null);
        if (user != null) {
            user.setPassword(passwordEncoder.encode("user123"));
            userRepository.save(user);
            log.info("Updated test user password: user@nebulamind.com / user123");
        } else {
            user = User.builder()
                    .username("user")
                    .email("user@nebulamind.com")
                    .password(passwordEncoder.encode("user123"))
                    .displayName("测试用户")
                    .role(User.Role.USER)
                    .status(User.UserStatus.ACTIVE)
                    .build();
            userRepository.save(user);
            log.info("Created test user: user@nebulamind.com / user123");
        }

        log.info("Dev data initialization complete.");
    }
}
