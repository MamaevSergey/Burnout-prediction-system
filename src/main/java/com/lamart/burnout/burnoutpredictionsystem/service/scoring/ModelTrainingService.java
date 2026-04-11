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

    // Внутренний класс для удобной передачи датасета
    public static class TrainingRecord {
        public double ee; // Индекс эмоционального истощения
        public double dp; // Индекс деперсонализации
        public double rpa; // Индекс редукции достижений
        public int actualBurnout; // 1 - выгорел, 0 - здоров

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

        // Гиперпараметры обучения
        double learningRate = 0.01;
        int epochs = 5000;

        // Инициализация случайных весов
        double w0 = 0.0;
        double w1 = 0.0;
        double w2 = 0.0;
        double w3 = 0.0;

        int n = dataset.size();

        // Цикл градиентного спуска
        for (int epoch = 0; epoch < epochs; epoch++) {
            double dw0 = 0, dw1 = 0, dw2 = 0, dw3 = 0;
            double totalLoss = 0;

            for (TrainingRecord record : dataset) {
                // Прямой проход
                double z = w0 + (w1 * record.ee) + (w2 * record.dp) + (w3 * record.rpa);
                double prediction = com.lamart.burnout.burnoutpredictionsystem.util.MathUtils.sigmoid(z);

                // Вычисление градиентов (ошибка * значение фичи)
                double error = prediction - record.actualBurnout;
                dw0 += error;
                dw1 += error * record.ee;
                dw2 += error * record.dp;
                dw3 += error * record.rpa;

                // Подсчет функции потерь (для логирования)
                // Защита от логарифма нуля: ограничиваем prediction
                double p = Math.max(1e-15, Math.min(1 - 1e-15, prediction));
                totalLoss += -(record.actualBurnout * Math.log(p) + (1 - record.actualBurnout) * Math.log(1 - p));
            }

            // Обновление весов (Шаг навстречу антиградиенту)
            w0 -= learningRate * (dw0 / n);
            w1 -= learningRate * (dw1 / n);
            w2 -= learningRate * (dw2 / n);
            w3 -= learningRate * (dw3 / n);

            if (epoch % 1000 == 0) {
                log.debug("Эпоха {}: Loss = {}", epoch, totalLoss / n);
            }
        }

        log.info("Обучение завершено. Получены веса: w0={}, w1={}, w2={}, w3={}", w0, w1, w2, w3);

        deactivateCurrentModel();

        // Создаем и сохраняем новую активную модель
        MlModel newModel = new MlModel();
        newModel.setW0Bias(w0);
        newModel.setW1Ee(w1);
        newModel.setW2Dp(w2);
        newModel.setW3Rpa(w3);
        newModel.setTrainedAt(LocalDateTime.now());
        newModel.setActive(true);

        return mlModelRepository.save(newModel);
    }

    @Transactional
    public void prepareDatasetAndTrain(HrSurveyUploadDto surveyDto) {
        List<TrainingRecord> dataset = new ArrayList<>();

        for (HrSurveyUploadDto.SurveyResult result : surveyDto.getResults()) {
            UUID empId = com.lamart.burnout.burnoutpredictionsystem.util.Anonymizer.hashToUuid(result.getEmail());
            burnoutScoreRepository.findFirstByEmployeeIdOrderByCalculatedAtDesc(empId);
        }

        if (!dataset.isEmpty()) {
            trainAndActivateNewModel(dataset);
        }
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

                    UUID empId = Anonymizer.hashToUuid(email);

                    burnoutScoreRepository.findFirstByEmployeeIdOrderByCalculatedAtDesc(empId)
                            .ifPresent(score -> {
                                dataset.add(new TrainingRecord(
                                        score.getEeIndex(),
                                        score.getDpIndex(),
                                        score.getRpaIndex(),
                                        isBurnedOut
                                ));
                            });
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
}