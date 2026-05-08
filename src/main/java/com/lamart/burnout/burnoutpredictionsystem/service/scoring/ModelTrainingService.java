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
import java.time.LocalDate;
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

        MlModel currentActive = mlModelRepository.findByIsActiveTrue().orElse(null);
        if (count0 == 0 || count1 == 0) {
            log.warn("В датасете представлен только один класс. Полноценное обучение невозможно.");
            deactivateCurrentModel();
            MlModel newModel = currentActive != null ? cloneModel(currentActive) : new MlModel();

            if (currentActive == null) {
                newModel.setW0Bias(-0.8);
                newModel.setW1Ee(1.2);
                newModel.setW2Dp(1.0);
                newModel.setW3Rpa(1.0);
            }

            if (count1 == 0) newModel.setW0Bias(newModel.getW0Bias() - 0.2);
            else newModel.setW0Bias(newModel.getW0Bias() + 0.2);

            return mlModelRepository.save(newModel);
        }

        double learningRate = 0.05;
        int maxEpochs = 5000;

        double baseW0 = -0.8;
        double baseW1 = 1.2;
        double baseW2 = 1.0;
        double baseW3 = 1.0;

        double w0 = baseW0;
        double w1 = baseW1;
        double w2 = baseW2;
        double w3 = baseW3;

        double lambda = Math.max(2.0, 50.0 / dataset.size());

        double weight0 = (double) dataset.size() / (2.0 * count0);
        double weight1 = (double) dataset.size() / (2.0 * count1);
        int n = dataset.size();

        double bestLoss = Double.MAX_VALUE;
        int epochsWithoutImprovement = 0;
        int patience = 200;

        for (int epoch = 0; epoch < maxEpochs; epoch++) {
            double dw0 = 0, dw1 = 0, dw2 = 0, dw3 = 0;
            double currentLoss = 0;

            for (TrainingRecord record : dataset) {
                double z = w0 + (w1 * record.ee) + (w2 * record.dp) + (w3 * record.rpa);
                double prediction = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.sigmoid(z);

                double error = prediction - record.actualBurnout;
                double classWeight = record.actualBurnout == 1 ? weight1 : weight0;

                dw0 += error * classWeight;
                dw1 += error * record.ee * classWeight;
                dw2 += error * record.dp * classWeight;
                dw3 += error * record.rpa * classWeight;

                double p = Math.max(1e-15, Math.min(1 - 1e-15, prediction));
                currentLoss += -classWeight * (record.actualBurnout * Math.log(p) + (1 - record.actualBurnout) * Math.log(1 - p));
            }

            // Байесовская регуляризация (тянем веса к экспертным, а не к нулю)
            dw0 += lambda * (w0 - baseW0);
            dw1 += lambda * (w1 - baseW1);
            dw2 += lambda * (w2 - baseW2);
            dw3 += lambda * (w3 - baseW3);

            currentLoss += (lambda / 2.0) * (Math.pow(w0 - baseW0, 2) + Math.pow(w1 - baseW1, 2) + Math.pow(w2 - baseW2, 2) + Math.pow(w3 - baseW3, 2));

            w0 -= learningRate * (dw0 / n);
            w1 -= learningRate * (dw1 / n);
            w2 -= learningRate * (dw2 / n);
            w3 -= learningRate * (dw3 / n);

            if (currentLoss < bestLoss - 0.0001) {
                bestLoss = currentLoss;
                epochsWithoutImprovement = 0;
            } else {
                epochsWithoutImprovement++;
            }

            if (epochsWithoutImprovement >= patience) {
                if (epoch % 500 == 0 || epoch < 10) {
                    log.info("Сработала ранняя остановка на эпохе {}. Ошибка стабилизировалась.", epoch);
                }
                break;
            }
        }

        evaluateModel(dataset, w0, w1, w2, w3);
        log.info("Обучение завершено. Веса: w0={}, w1={}, w2={}, w3={}",
                String.format("%.4f", w0), String.format("%.4f", w1), String.format("%.4f", w2), String.format("%.4f", w3));

        checkModelDrift(currentActive, w0, w1, w2, w3);
        deactivateCurrentModel();

        MlModel newModel = new MlModel();
        newModel.setW0Bias(w0);
        newModel.setW1Ee(w1);
        newModel.setW2Dp(w2);
        newModel.setW3Rpa(w3);
        newModel.setTrainedAt(LocalDateTime.now());
        newModel.setVersion("v1.0-" + LocalDate.now());
        newModel.setActive(true);

        return mlModelRepository.save(newModel);
    }

    private MlModel cloneModel(MlModel source) {
        MlModel clone = new MlModel();
        clone.setW0Bias(source.getW0Bias());
        clone.setW1Ee(source.getW1Ee());
        clone.setW2Dp(source.getW2Dp());
        clone.setW3Rpa(source.getW3Rpa());
        clone.setTrainedAt(LocalDateTime.now());
        clone.setVersion("v1.0-fb-" + LocalDate.now());
        clone.setActive(true);
        return clone;
    }

    private void evaluateModel(List<TrainingRecord> dataset, double w0, double w1, double w2, double w3) {
        int tp = 0, tn = 0, fp = 0, fn = 0;
        for (TrainingRecord record : dataset) {
            double z = w0 + (w1 * record.ee) + (w2 * record.dp) + (w3 * record.rpa);
            double prediction = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.sigmoid(z);
            int predictedClass = prediction >= 0.6 ? 1 : 0;

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
        MlModel currentActive = mlModelRepository.findByIsActiveTrue().orElse(null);
        if (currentActive != null) {
            currentActive.setActive(false);
            mlModelRepository.saveAndFlush(currentActive);
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
                    String hashString = columns[0].trim();
                    int isBurnedOut = Integer.parseInt(columns[1].trim());

                    UUID empId = UUID.fromString(hashString);

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