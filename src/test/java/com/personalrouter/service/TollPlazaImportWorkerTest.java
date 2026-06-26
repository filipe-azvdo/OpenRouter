package com.personalrouter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalrouter.dto.RowError;
import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.repository.TollPlazaImportRepository;
import com.personalrouter.service.TollPlazaReconciliationService.ReconciliationCounts;
import com.personalrouter.service.csv.CsvParseResult;
import com.personalrouter.service.csv.TollPlazaCsvParser;
import com.personalrouter.service.csv.TollPlazaCsvRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TollPlazaImportWorkerTest {

    @Mock private TollPlazaImportRepository importRepository;
    @Mock private TollPlazaCsvParser parser;
    @Mock private TollPlazaReconciliationService reconciliationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Captor private ArgumentCaptor<TollPlazaImport> captor;

    private TollPlazaImportWorkerImpl newWorker() {
        return new TollPlazaImportWorkerImpl(
                importRepository, parser,
                reconciliationService, objectMapper);
    }

    private static TollPlazaCsvRow row() {
        return new TollPlazaCsvRow("C", "N", 2007, "BR-381", "MG",
                new BigDecimal("900.9"), "Cidade", "Principal", "Crescente", -22.6, -46.0);
    }

    @Test
    void happyPathMarksProcessingThenSuccessWithCounts() {
        UUID id = UUID.randomUUID();
        TollPlazaImport job = TollPlazaImport.builder()
                .id(id).contentHash("h")
                .status(ImportStatus.PENDING).build();
        when(importRepository.findById(id))
                .thenReturn(Optional.of(job));
        when(importRepository.save(any(TollPlazaImport.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(parser.parse(any()))
                .thenReturn(new CsvParseResult(List.of(row()), List.of(), 1));
        when(reconciliationService.reconcile(any()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));
        TollPlazaImportWorker worker = newWorker();

        worker.process(id, new byte[] {1, 2, 3});

        verify(importRepository, times(2)).save(captor.capture());
        TollPlazaImport finalState = captor.getAllValues().get(1);
        assertThat(finalState.getStatus()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(finalState.getInserted()).isEqualTo(1);
        assertThat(finalState.getTotalRows()).isEqualTo(1);
        assertThat(finalState.getFinishedAt()).isNotNull();
    }

    @Test
    void serializesRowErrorsIntoJob() throws Exception {
        UUID id = UUID.randomUUID();
        TollPlazaImport job = TollPlazaImport.builder()
                .id(id).contentHash("h")
                .status(ImportStatus.PENDING).build();
        when(importRepository.findById(id))
                .thenReturn(Optional.of(job));
        when(importRepository.save(any(TollPlazaImport.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(parser.parse(any())).thenReturn(new CsvParseResult(
                List.of(row()),
                List.of(new RowError(2, "latitude inválido")), 2));
        when(reconciliationService.reconcile(any()))
                .thenReturn(new ReconciliationCounts(1, 0, 0));
        TollPlazaImportWorker worker = newWorker();

        worker.process(id, new byte[] {1});

        verify(importRepository, times(2)).save(captor.capture());
        String errorsJson = captor.getAllValues().get(1).getErrors();
        assertThat(errorsJson).contains("latitude inválido").contains("\"line\":2");
    }

    @Test
    void reconciliationFailureMarksFailed() {
        UUID id = UUID.randomUUID();
        TollPlazaImport job = TollPlazaImport.builder()
                .id(id).contentHash("h")
                .status(ImportStatus.PENDING).build();
        when(importRepository.findById(id))
                .thenReturn(Optional.of(job));
        when(importRepository.save(any(TollPlazaImport.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(parser.parse(any()))
                .thenReturn(new CsvParseResult(List.of(row()), List.of(), 1));
        when(reconciliationService.reconcile(any()))
                .thenThrow(new RuntimeException("boom"));
        TollPlazaImportWorker worker = newWorker();

        worker.process(id, new byte[] {1});

        verify(importRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(ImportStatus.FAILED);
    }
}
