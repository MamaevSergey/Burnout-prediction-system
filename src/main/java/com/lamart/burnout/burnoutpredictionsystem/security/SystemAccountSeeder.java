package com.lamart.burnout.burnoutpredictionsystem.security;

import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.entity.SystemUser;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.SystemUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class SystemAccountSeeder implements CommandLineRunner {
    private final SystemUserRepository systemUserRepository;
    private final MlModelRepository mlModelRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.admin-login}")
    private String admin_login;

    @Value("${app.security.admin-password}")
    private String admin_password;

    @Value("${app.security.hr-login}")
    private String hr_login;

    @Value("${app.security.hr-password}")
    private String hr_password;

    @Value("${app.security.viewer-login}")
    private String viewer_login;

    @Value("${app.security.viewer-password}")
    private String viewer_password;

    @Override
    public void run(String... args) {
        seedUsers();
        seedBaseModel();
    }

    private void seedUsers() {
        if (systemUserRepository.count() == 0) {
            SystemUser admin = new SystemUser();
            admin.setUsername(admin_login);
            admin.setPasswordHash(passwordEncoder.encode(admin_password));
            admin.setRole("ROLE_ADMIN");
            systemUserRepository.save(admin);

            SystemUser hr = new SystemUser();
            hr.setUsername(hr_login);
            hr.setPasswordHash(passwordEncoder.encode(hr_password));
            hr.setRole("ROLE_HR");
            systemUserRepository.save(hr);

            SystemUser viewer = new SystemUser();
            viewer.setUsername(viewer_login);
            viewer.setPasswordHash(passwordEncoder.encode(viewer_password));
            viewer.setRole("ROLE_VIEWER");
            systemUserRepository.save(viewer);
        }
    }

    private void seedBaseModel() {
        if (mlModelRepository.count() == 0) {
            MlModel baseModel = new MlModel();
            baseModel.setVersion("v1.0.0-base");
            baseModel.setW0Bias(-0.8);
            baseModel.setW1Ee(1.2);
            baseModel.setW2Dp(1.0);
            baseModel.setW3Rpa(1.0);
            baseModel.setActive(true);
            baseModel.setTrainedAt(LocalDateTime.now());
            mlModelRepository.save(baseModel);
        }
    }
}