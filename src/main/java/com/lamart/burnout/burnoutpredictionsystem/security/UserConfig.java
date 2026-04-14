package com.lamart.burnout.burnoutpredictionsystem.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class UserConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails hr = User.builder()
                .username("hr_admin")
                .password(passwordEncoder.encode("hr_password123"))
                .roles("HR")
                .build();

        UserDetails admin = User.builder()
                .username("sys_admin")
                .password(passwordEncoder.encode("admin_super_secret"))
                .roles("ADMIN", "HR")
                .build();

        return new InMemoryUserDetailsManager(hr, admin);
    }
}
