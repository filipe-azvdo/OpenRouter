package com.personalrouter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalrouter.dto.RowError;
import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.repository.TollPlazaImportRepository;
import com.personalrouter.service.TollPlazaReconciliationService.ReconciliationCounts;
import com.personalrouter.service.csv.CsvParseResult;
import com.personalrouter.service.csv.TollPlazaCsvParser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TollPlazaImportWorker {

    private final TollPlazaImportRepository importRepository;
    private final TollPlazaCsvParser parser;
    private final TollPlazaReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    @Async("tollImportExecutor")
    public void process(UUID importId, byte[] content) {
        TollPlazaImport job = importRepository.findById(importId).orElse(null);
        if (job == null) {
            log.warn("Import {} não encontrado; abortando worker", importId);
            return;
        }
        job.setStatus(ImportStatus.PROCESSING);
        importRepository.save(job);

        try {
            CsvParseResult parsed = parser.parse(content);
            ReconciliationCounts counts = reconciliationService.reconcile(parsed.rows());

            job.setInserted(counts.inserted());
            job.setReactivated(counts.reactivated());
            job.setUpdated(counts.updated());
            job.setDeactivated(counts.deactivated());
            job.setTotalRows(parsed.totalRows());
            job.setErrors(serialize(parsed.errors()));
            job.setStatus(ImportStatus.SUCCESS);
            job.setFinishedAt(Instant.now());
            importRepository.save(job);
            log.info("Import {} concluído: {}", importId, counts);
        } catch (RuntimeException e) {
            log.error("Import {} falhou: {}", importId, e.getMessage(), e);
            job.setStatus(ImportStatus.FAILED);
            job.setFinishedAt(Instant.now());
            importRepository.save(job);
        }
    }

    private String serialize(List<RowError> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar erros do import", e);
        }
    }
}
