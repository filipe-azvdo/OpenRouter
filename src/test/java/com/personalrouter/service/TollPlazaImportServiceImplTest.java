package com.personalrouter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalrouter.dto.TollPlazaImportResultDto;
import com.personalrouter.exception.InvalidCsvException;
import com.personalrouter.exception.TollPlazaImportNotFoundException;
import com.personalrouter.mapper.TollPlazaImportMapper;
import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.repository.TollPlazaImportRepository;
import com.personalrouter.service.csv.TollPlazaCsvParser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TollPlazaImportServiceImplTest {

    @Mock private TollPlazaImportRepository importRepository;
    @Mock private TollPlazaCsvParser parser;
    @Mock private TollPlazaImportWorker worker;
    @Mock private TollPlazaImportMapper mapper;
    @InjectMocks private TollPlazaImportServiceImpl service;

    private static final byte[] CONTENT = "header\nrow".getBytes();

    @Test
    void invalidStructureRejectsBeforeAnyWrite() {
        org.mockito.Mockito.doThrow(new InvalidCsvException("vazio"))
                .when(parser).validateStructure(any());

        assertThatThrownBy(() -> service.importCsv(CONTENT)).isInstanceOf(InvalidCsvException.class);
        verify(importRepository, never()).save(any());
        verify(worker, never()).process(any(), any());
    }

    @Test
    void duplicateHashReturnsExistingWithoutDispatch() {
        TollPlazaImport existing = TollPlazaImport.builder()
                .id(UUID.randomUUID()).contentHash("h").status(ImportStatus.SUCCESS).build();
        when(importRepository.findFirstByContentHashAndStatusIn(any(), any()))
                .thenReturn(Optional.of(existing));
        when(mapper.toDto(existing)).thenReturn(dto(existing));

        ImportSubmission submission = service.importCsv(CONTENT);

        assertThat(submission.created()).isFalse();
        verify(worker, never()).process(any(), any());
        verify(importRepository, never()).save(any());
    }

    @Test
    void newHashCreatesPendingJobAndDispatchesWorker() {
        when(importRepository.findFirstByContentHashAndStatusIn(any(), any())).thenReturn(Optional.empty());
        when(importRepository.save(any(TollPlazaImport.class))).thenAnswer(i -> {
            TollPlazaImport j = i.getArgument(0);
            ReflectionTestUtils.setField(j, "id", UUID.randomUUID());
            return j;
        });
        when(mapper.toDto(any())).thenAnswer(i -> dto(i.getArgument(0)));

        ImportSubmission submission = service.importCsv(CONTENT);

        assertThat(submission.created()).isTrue();
        assertThat(submission.result().status()).isEqualTo("PENDING");
        verify(worker).process(any(UUID.class), eq(CONTENT));
    }

    @Test
    void getImportThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(importRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getImport(id))
                .isInstanceOf(TollPlazaImportNotFoundException.class);
    }

    private static TollPlazaImportResultDto dto(TollPlazaImport e) {
        return new TollPlazaImportResultDto(e.getId(), e.getStatus().name(), e.getContentHash(),
                null, null, null, null, null, List.of(), null, null);
    }
}
