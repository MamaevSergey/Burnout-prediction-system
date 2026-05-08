package com.lamart.burnout.burnoutpredictionsystem.entity;

import com.lamart.burnout.burnoutpredictionsystem.config.EncryptionConverter;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "system_settings")
@Data
public class SystemSettings {
    @Id
    private Long id = 1L;

    private Double greenThreshold = 0.4;
    private Double yellowThreshold = 0.75;
    private Double scoringAlpha = 0.1;

    private String jiraUrl = "https://company.atlassian.net";
    private String githubOwner = "OrganizationName";

    // Секреты хранятся в зашифрованном виде
    @Convert(converter = EncryptionConverter.class)
    private String jiraUsername;

    @Convert(converter = EncryptionConverter.class)
    private String jiraToken;

    @Convert(converter = EncryptionConverter.class)
    private String githubToken;

    private String timezone = "Asia/Yekaterinburg";
    private Integer minCommitLength = 8;

    @Column(columnDefinition = "TEXT")
    private String boilerplateWords = "fix,update,\\.,исправил,обновил,test,vip,фича";

    private String doneStatuses = "Done,Готово,Closed,Resolved";
    private Long isolatedEventDurationSeconds = 1800L;
}
