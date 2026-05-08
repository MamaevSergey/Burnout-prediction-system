package com.lamart.burnout.burnoutpredictionsystem.service.etl.mock;

import com.lamart.burnout.burnoutpredictionsystem.entity.Employee;

public record EmployeeProfile(
        Employee employee,
        ProfileType type,
        double baseProductivity,
        boolean workStyleEarlyBird
) {}
