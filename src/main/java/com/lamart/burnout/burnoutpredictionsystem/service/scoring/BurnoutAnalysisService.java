package com.lamart.burnout.burnoutpredictionsystem.service.scoring;

import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeDetailDto;
import com.lamart.burnout.burnoutpredictionsystem.dto.EmployeeSummaryDto;
import com.lamart.burnout.burnoutpredictionsystem.entity.BurnoutScore;
import com.lamart.burnout.burnoutpredictionsystem.entity.DailyMetric;
import com.lamart.burnout.burnoutpredictionsystem.entity.GitCommit;
import com.lamart.burnout.burnoutpredictionsystem.repository.BurnoutScoreRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.DailyMetricRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.GitCommitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BurnoutAnalysisService {

    private final BurnoutScoreRepository burnoutScoreRepository;
    private final DailyMetricRepository dailyMetricRepository;
    private final GitCommitRepository gitCommitRepository;

    public List<EmployeeSummaryDto> getAllEmployeesSummary() {
        return burnoutScoreRepository.findLatestScores().stream()
                .map(score -> new EmployeeSummaryDto(
                        score.getEmployee().getId(),
                        score.getEmployee().getTeam() != null ? score.getEmployee().getTeam().getName() : "Без команды",
                        score.getEmployee().getRole(),
                        score.getRiskProbability(),
                        score.getStatusColor()
                ))
                .collect(Collectors.toList());
    }

    public EmployeeDetailDto getEmployeeDetail(UUID employeeId) {
        BurnoutScore score = burnoutScoreRepository.findAll().stream()
                .filter(s -> s.getEmployee().getId().equals(employeeId))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new RuntimeException("Данные для сотрудника не найдены"));

        LocalDate targetDate = score.getTargetDate();
        LocalDate startDate = targetDate.minusDays(30);

        List<DailyMetric> metrics = dailyMetricRepository.findAllByEmployeeIdAndDateBetween(employeeId, startDate, targetDate);

        double avgPrLeadTimeHours = metrics.stream()
                .filter(m -> m.getPrLeadTimeAvgMinutes() > 0)
                .mapToDouble(DailyMetric::getPrLeadTimeAvgMinutes)
                .average().orElse(0.0) / 60.0;

        double avgActivitySpanHours = metrics.stream()
                .filter(m -> m.getActivitySpanSeconds() > 0)
                .mapToLong(DailyMetric::getActivitySpanSeconds)
                .average().orElse(0.0) / 3600.0;

        double avgJiraEffort = metrics.stream()
                .filter(m -> m.getJiraEffortScore() > 0)
                .mapToDouble(DailyMetric::getJiraEffortScore)
                .average().orElse(0.0);

        double avgReopenRate = metrics.stream()
                .mapToDouble(DailyMetric::getReopenRate)
                .average().orElse(0.0) * 100.0;

        double avgBadCommit = metrics.stream()
                .mapToDouble(DailyMetric::getBadCommitRatio)
                .average().orElse(0.0) * 100.0;

        double avgStagnationDays = metrics.stream()
                .mapToLong(DailyMetric::getTaskStagnationSeconds)
                .average().orElse(0.0) / 86400.0;

        DailyMetric lastMetric = metrics.isEmpty() ? null : metrics.getLast();

        boolean prSpiked = lastMetric != null && (lastMetric.getPrLeadTimeAvgMinutes() > avgPrLeadTimeHours * 60);
        boolean stagSpiked = lastMetric != null && (lastMetric.getTaskStagnationSeconds() > avgStagnationDays * 86400);
        boolean reopenSpiked = lastMetric != null && (lastMetric.getReopenRate() * 100 > avgReopenRate);

        boolean nightWork = lastMetric != null && lastMetric.getNightEventsCount() > 0;
        LocalDate oneWeekAgo = targetDate.minusDays(7);
        boolean weekendWork = metrics.stream()
                .filter(m -> !m.getDate().isBefore(oneWeekAgo))
                .anyMatch(m -> m.getWeekendEventsCount() > 0);

        List<GitCommit> monthCommits = gitCommitRepository.findByEmployeeIdInAndCommittedAtBetween(
                List.of(employeeId), startDate.atStartOfDay(), targetDate.atTime(23, 59, 59));
        double commitsPerDay = monthCommits.size() / 30.0;

        long commitsYesterday = monthCommits.stream()
                .filter(c -> !c.getCommittedAt()
                .isBefore(targetDate.minusDays(1).atStartOfDay()) &&
                        c.getCommittedAt()
                .isBefore(targetDate.atStartOfDay()))
                .count();

        long commitsDayBefore = monthCommits.stream()
                .filter(c -> !c.getCommittedAt()
                .isBefore(targetDate.minusDays(2).atStartOfDay()) &&
                        c.getCommittedAt()
                .isBefore(targetDate.minusDays(1).atStartOfDay()))
                .count();

        boolean commitsTrendUp = commitsYesterday >= commitsDayBefore;

        String modelVer = score.getModel() != null ? score.getModel().getVersion() : "v1.0.0-beta";
        int totalConflicts = metrics.stream().mapToInt(DailyMetric::getMergeConflictsCount).sum();

        Map<String, String> interpretations = Map.of(
                "EE", generateEEAnalysis(score.getEeIndex(), avgActivitySpanHours, nightWork, weekendWork),
                "DP", generateDPAnalysis(score.getDpIndex(), avgBadCommit, avgJiraEffort),
                "RPA", generateRPAAnalysis(score.getRpaIndex(), avgPrLeadTimeHours, avgStagnationDays, avgReopenRate, totalConflicts, prSpiked, stagSpiked, reopenSpiked)
        );

        return new EmployeeDetailDto(
                score.getEmployee().getId(),
                score.getEmployee().getTeam() != null ? score.getEmployee().getTeam().getName() : "Без команды",
                score.getEmployee().getRole(),
                score.getRiskProbability(),
                score.getStatusColor(),
                score.getEeIndex(),
                score.getDpIndex(),
                score.getRpaIndex(),
                avgPrLeadTimeHours,
                weekendWork,
                nightWork,
                avgReopenRate,
                commitsPerDay,
                commitsTrendUp,
                modelVer,
                score.getCalculatedAt(),
                interpretations
        );
    }

    private String generateEEAnalysis(double ee, double avgSpanHours, boolean nightWork, boolean weekendWork) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p class='mb-3'>").append(getEEMainText(ee)).append("</p>");
        sb.append("<ul class='list-disc pl-5 space-y-1.5 text-xs text-gray-600'>");

        boolean isHighRisk = ee > 1.0;

        sb.append("<li><b>Средняя рабочая сессия:</b> ").append(String.format("%.1f", avgSpanHours)).append(" ч/день ");
        sb.append((avgSpanHours >= 8.5 && isHighRisk) ? "<span class='text-red-600 font-bold'>(Аномалия для сотрудника)</span>" : "<span class='text-emerald-600 font-bold'>(В привычной норме)</span>").append("</li>");

        sb.append("<li><b>Работа ночью:</b> ").append((nightWork && isHighRisk) ? "<span class='text-red-600 font-bold'>Участилась</span>" : "<span class='text-emerald-600 font-bold'>В пределах нормы</span>").append("</li>");
        sb.append("<li><b>Работа в выходные:</b> ").append((weekendWork && isHighRisk) ? "<span class='text-red-600 font-bold'>Сбой режима отдыха</span>" : "<span class='text-emerald-600 font-bold'>Редкие эпизоды</span>").append("</li>");

        sb.append("</ul>");
        return sb.toString();
    }

    private String generateDPAnalysis(double dp, double avgBadCommit, double avgJiraEffort) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p class='mb-3'>").append(getDPMainText(dp)).append("</p>");
        sb.append("<ul class='list-disc pl-5 space-y-1.5 text-xs text-gray-600'>");

        boolean isHighRisk = dp > 1.0;

        sb.append("<li><b>Доля формальных/пустых коммитов:</b> ").append(String.format("%.1f%%", avgBadCommit)).append(" ");
        sb.append((avgBadCommit > 5.0 && isHighRisk) ? "<span class='text-red-600 font-bold'>(Хуже обычного)</span>" : "<span class='text-emerald-600 font-bold'>(На личном уровне)</span>").append("</li>");

        sb.append("<li><b>Вклад в обсуждение задач (баллы):</b> ").append(String.format("%.1f", avgJiraEffort)).append(" ");
        sb.append((isHighRisk) ? "<span class='text-red-600 font-bold'>(Снижение вовлеченности)</span>" : "<span class='text-emerald-600 font-bold'>(Стабильный вклад)</span>").append("</li>");

        sb.append("</ul>");
        return sb.toString();
    }

    private String generateRPAAnalysis(double rpa, double leadTimeHours, double stagnationDays, double reopenRate, int conflicts, boolean prSpiked, boolean stagSpiked, boolean reopenSpiked) {
        StringBuilder sb = new StringBuilder();
        sb.append("<p class='mb-3'>").append(getRPAMainText(rpa)).append("</p>");
        sb.append("<ul class='list-disc pl-5 space-y-1.5 text-xs text-gray-600'>");

        boolean isHighRisk = rpa > 1.0;

        sb.append("<li><b>Средний Lead Time (PR):</b> ").append(String.format("%.1f", leadTimeHours)).append(" ч. ");
        sb.append((isHighRisk && prSpiked) ? "<span class='text-red-600 font-bold'>(Резкое замедление)</span>" : "<span class='text-emerald-600 font-bold'>(В привычном темпе)</span>").append("</li>");

        sb.append("<li><b>Среднее время стагнации задач:</b> ").append(String.format("%.1f", stagnationDays)).append(" дн. ");
        sb.append((isHighRisk && stagSpiked) ? "<span class='text-red-600 font-bold'>(Задачи стали зависать)</span>" : "<span class='text-emerald-600 font-bold'>(В пределах нормы)</span>").append("</li>");

        sb.append("<li><b>Возвраты задач (Reopen Rate):</b> ").append(String.format("%.1f%%", reopenRate)).append(" ");
        sb.append((isHighRisk && reopenSpiked) ? "<span class='text-red-600 font-bold'>(Упало личное качество)</span>" : "<span class='text-emerald-600 font-bold'>(Качество стабильно)</span>").append("</li>");

        sb.append("<li><b>Конфликты слияния за месяц:</b> ").append(conflicts).append(" шт.</li>");

        sb.append("</ul>");
        return sb.toString();
    }

    private String getEEMainText(double ee) {
        if (ee < -1.5) return "Активность сотрудника значительно ниже его собственной нормы. Возможен отпуск или простой.";
        if (ee < -0.5) return "Здоровый уровень загрузки, ниже личного среднего показателя. Риск переутомления минимален.";
        if (ee <= 0.5) return "Стандартная рабочая нагрузка. Активность полностью соответствует привычному ритму сотрудника.";
        if (ee <= 1.5) return "Повышенная интенсивность работы по сравнению с прошлыми периодами. Стоит обратить внимание на загрузку.";
        return "Критическое истощение! Рабочая активность резко и аномально выросла по сравнению с личной нормой сотрудника.";
    }

    private String getDPMainText(double dp) {
        if (dp < -1.5) return "Сотрудник проявляет аномально высокую вовлеченность в коммуникацию относительно своего базового стиля.";
        if (dp < -0.5) return "Хорошая вовлеченность в процессы. Коммуникация чуть активнее личной нормы.";
        if (dp <= 0.5) return "Нормальный, стабильный уровень деловой коммуникации для данного сотрудника.";
        if (dp <= 1.5) return "Первые признаки отстраненности. Сотрудник стал общаться заметно суше и формальнее, чем обычно.";
        return "Резкая деперсонализация. Сотрудник практически прекратил привычную коммуникацию, качество обсуждений упало.";
    }

    private String getRPAMainText(double rpa) {
        if (rpa < -1.5) return "Задачи закрываются аномально быстрее обычного темпа сотрудника.";
        if (rpa < -0.5) return "Высокая личная результативность. Скорость работы выше привычной нормы.";
        if (rpa <= 0.5) return "Стабильный темп. Качество и скорость работы соответствуют историческим показателям сотрудника.";
        if (rpa <= 1.5) return "Заметна личная стагнация. Сотрудник стал закрывать задачи медленнее, чем делал это в прошлом месяце.";
        return "Критическое падение продуктивности. Метрики скорости и качества работы резко ухудшились по сравнению с личной нормой.";
    }
}
