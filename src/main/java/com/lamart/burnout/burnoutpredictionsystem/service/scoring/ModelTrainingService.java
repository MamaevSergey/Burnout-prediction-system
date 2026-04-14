package com.lamart.burnout.burnoutpredictionsystem.service.scoring;

import com.lamart.burnout.burnoutpredictionsystem.entity.MlModel;
import com.lamart.burnout.burnoutpredictionsystem.integration.dto.HrSurveyUploadDto;
import com.lamart.burnout.burnoutpredictionsystem.repository.BurnoutScoreRepository;
import com.lamart.burnout.burnoutpredictionsystem.repository.MlModelRepository;
import com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTrainingService {
    private final MlModelRepository mlModelRepository;
    private final BurnoutScoreRepository burnoutScoreRepository;
    private final Anonymizer anonymizer;

    public static class TrainingRecord {
        public double ee;
        public double dp;
        public double rpa;
        public int actualBurnout;

        public TrainingRecord(double ee, double dp, double rpa, int actualBurnout) {
            this.ee = ee;
            this.dp = dp;
            this.rpa = rpa;
            this.actualBurnout = actualBurnout;
        }
    }

    @Transactional
    public MlModel trainAndActivateNewModel(List<TrainingRecord> dataset) {
        log.info("Начинаем обучение модели на датасете из {} записей...", dataset.size());

        int count1 = (int) dataset.stream().filter(r -> r.actualBurnout == 1).count();
        int count0 = dataset.size() - count1;

        // Защита от полного дисбаланса классов
        if (count0 == 0 || count1 == 0) {
            throw new IllegalArgumentException("Ошибка: датасет должен содержать как выгоревших (1), так и здоровых (0) сотрудников.");
        }

        // Гиперпараметры
        double learningRate = 0.01;
        int epochs = 5000;
        double lambda = 0.1; // L2-регуляризация (Ridge)

        // Взвешивание классов для борьбы с дисбалансом
        double weight0 = (double) dataset.size() / (2.0 * count0);
        double weight1 = (double) dataset.size() / (2.0 * count1);

        double w0 = 0.0, w1 = 0.0, w2 = 0.0, w3 = 0.0;
        int n = dataset.size();

        for (int epoch = 0; epoch < epochs; epoch++) {
            double dw0 = 0, dw1 = 0, dw2 = 0, dw3 = 0;
            double totalLoss = 0;

            for (TrainingRecord record : dataset) {
                double z = w0 + (w1 * record.ee) + (w2 * record.dp) + (w3 * record.rpa);
                double prediction = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.sigmoid(z);

                double error = prediction - record.actualBurnout;
                double classWeight = record.actualBurnout == 1 ? weight1 : weight0; // Применяем вес класса

                dw0 += error * classWeight;
                dw1 += error * record.ee * classWeight;
                dw2 += error * record.dp * classWeight;
                dw3 += error * record.rpa * classWeight;

                double p = Math.max(1e-15, Math.min(1 - 1e-15, prediction));
                totalLoss += -classWeight * (record.actualBurnout * Math.log(p) + (1 - record.actualBurnout) * Math.log(1 - p));
            }

            // Добавляем штраф L2-регуляризации
            dw1 += lambda * w1;
            dw2 += lambda * w2;
            dw3 += lambda * w3;

            w0 -= learningRate * (dw0 / n);
            w1 -= learningRate * (dw1 / n);
            w2 -= learningRate * (dw2 / n);
            w3 -= learningRate * (dw3 / n);
        }

        evaluateModel(dataset, w0, w1, w2, w3);
        log.info("Обучение завершено. Веса: w0={}, w1={}, w2={}, w3={}", w0, w1, w2, w3);

        MlModel currentActive = mlModelRepository.findByIsActiveTrue();
        checkModelDrift(currentActive, w0, w1, w2, w3);

        deactivateCurrentModel();

        MlModel newModel = new MlModel();
        newModel.setW0Bias(w0);
        newModel.setW1Ee(w1);
        newModel.setW2Dp(w2);
        newModel.setW3Rpa(w3);
        newModel.setTrainedAt(LocalDateTime.now());
        newModel.setActive(true);

        return mlModelRepository.save(newModel);
    }

    private void evaluateModel(List<TrainingRecord> dataset, double w0, double w1, double w2, double w3) {
        int tp = 0, tn = 0, fp = 0, fn = 0;
        for (TrainingRecord record : dataset) {
            double z = w0 + (w1 * record.ee) + (w2 * record.dp) + (w3 * record.rpa);
            double prediction = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.sigmoid(z);
            int predictedClass = prediction >= 0.5 ? 1 : 0;

            if (predictedClass == 1 && record.actualBurnout == 1) tp++;
            else if (predictedClass == 0 && record.actualBurnout == 0) tn++;
            else if (predictedClass == 1 && record.actualBurnout == 0) fp++;
            else fn++;
        }

        double accuracy = (double) (tp + tn) / dataset.size();
        double precision = (tp + fp) == 0 ? 0 : (double) tp / (tp + fp);
        double recall = (tp + fn) == 0 ? 0 : (double) tp / (tp + fn);
        double f1Score = (precision + recall) == 0 ? 0 : 2 * (precision * recall) / (precision + recall);

        log.info("=== Метрики качества модели (Eval Pipeline) ===");
        log.info("Accuracy:  {}", String.format("%.2f", accuracy));
        log.info("Precision: {}", String.format("%.2f", precision));
        log.info("Recall:    {}", String.format("%.2f", recall));
        log.info("F1-Score:  {}", String.format("%.2f", f1Score));
        log.info("===============================================");
    }

    @Transactional
    public void prepareDatasetAndTrain(HrSurveyUploadDto surveyDto) {
        List<TrainingRecord> dataset = new ArrayList<>();

        for (HrSurveyUploadDto.SurveyResult result : surveyDto.getResults()) {
            UUID empId = anonymizer.hashToUuid(result.getEmail());
            // Добавляем найденные данные в dataset
            burnoutScoreRepository.findTopByEmployeeIdOrderByTargetDateDesc(empId)
                    .ifPresent(score -> dataset.add(new TrainingRecord(
                            score.getEeIndex(),
                            score.getDpIndex(),
                            score.getRpaIndex(),
                            result.getIsBurnedOut()
                    )));
        }

        if (dataset.size() < 7) {
            throw new IllegalArgumentException("Недостаточно данных для обучения. Минимум: 7 (найдено: " + dataset.size() + ")");
        }
        trainAndActivateNewModel(dataset);
    }

    @Transactional
    public void deactivateCurrentModel() {
        MlModel currentActive = mlModelRepository.findByIsActiveTrue();
        if (currentActive != null) {
            currentActive.setActive(false);
            mlModelRepository.save(currentActive);
        }
    }

    @Transactional
    public void processCsvAndTrain(MultipartFile file) {
        List<TrainingRecord> dataset = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] columns = line.split(",");

                if (columns.length >= 2) {
                    String email = columns[0].trim();
                    int isBurnedOut = Integer.parseInt(columns[1].trim());

                    UUID empId = anonymizer.hashToUuid(email);

                    burnoutScoreRepository.findTopByEmployeeIdOrderByTargetDateDesc(empId)
                            .ifPresent(score -> dataset.add(new TrainingRecord(
                                    score.getEeIndex(),
                                    score.getDpIndex(),
                                    score.getRpaIndex(),
                                    isBurnedOut
                            )));
                }
            }
        } catch (Exception exception) {
            throw new RuntimeException("Ошибка при парсинге CSV файла: " + exception.getMessage());
        }

        if (dataset.size() < 7) {
            throw new IllegalArgumentException("Недостаточно данных для обучения. Найдено сопоставлений: " + dataset.size() + ". Минимум: 7");
        }

        trainAndActivateNewModel(dataset);
    }

    private void checkModelDrift(MlModel oldModel, double newW0, double newW1, double newW2, double newW3) {
        if (oldModel == null) return;
        // Евклидово расстояние между векторами весов
        double driftScore = Math.sqrt(
                Math.pow(oldModel.getW0Bias() - newW0, 2) +
                Math.pow(oldModel.getW1Ee() - newW1, 2) +
                Math.pow(oldModel.getW2Dp() - newW2, 2) +
                Math.pow(oldModel.getW3Rpa() - newW3, 2)
        );

        log.info("Мониторинг Model Drift");
        log.info("Смещение весов (Drift Score): {}", String.format("%.4f", driftScore));

        if (driftScore > 0.5) {
            log.warn("Внимание! Обнаружено значительное смещение данных (Data Drift)." +
                    "Поведение сотрудников резко изменилось. Рекомендуется ручной аудит метрик!");
        } else {
            log.info("Смещение в пределах нормы (стабильно).");
        }
    }
}