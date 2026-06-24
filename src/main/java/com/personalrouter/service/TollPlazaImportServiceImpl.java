package com.personalrouter.service;

import com.personalrouter.dto.TollPlazaImportResultDto;
import com.personalrouter.exception.TollPlazaImportNotFoundException;
import com.personalrouter.mapper.TollPlazaImportMapper;
import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.repository.TollPlazaImportRepository;
import com.personalrouter.service.csv.ContentHash;
import com.personalrouter.service.csv.TollPlazaCsvParser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TollPlazaImportServiceImpl implements TollPlazaImportService {

    private static final List<ImportStatus> DEDUP_STATUSES =
            List.of(ImportStatus.PENDING, ImportStatus.PROCESSING, ImportStatus.SUCCESS);

    private final TollPlazaImportRepository importRepository;
    private final TollPlazaCsvParser parser;
    private final ApplicationEventPublisher events;
    private final TollPlazaImportMapper mapper;

    @Override
    @Transactional
    public ImportSubmission importCsv(byte[] content) {
        parser.validateStructure(content);
        String hash = ContentHash.sha256Hex(content);

        Optional<TollPlazaImport> existing =
                importRepository.findFirstByContentHashAndStatusIn(hash, DEDUP_STATUSES);
        if (existing.isPresent()) {
            log.info("Import idempotente: hash {} já existe ({})", hash, existing.get().getStatus());
            return new ImportSubmission(mapper.toDto(existing.get()), false);
        }

        TollPlazaImport job = importRepository.save(TollPlazaImport.builder()
                .contentHash(hash)
                .status(ImportStatus.PENDING)
                .build());
        log.info("Import {} aceito (PENDING), disparando worker", job.getId());
        events.publishEvent(new TollPlazaImportCreatedEvent(job.getId(), content));
        return new ImportSubmission(mapper.toDto(job), true);
    }

    @Override
    @Transactional(readOnly = true)
    public TollPlazaImportResultDto getImport(UUID importId) {
        TollPlazaImport job = importRepository.findById(importId)
                .orElseThrow(() -> new TollPlazaImportNotFoundException(
                        "Import não encontrado: " + importId));
        return mapper.toDto(job);
    }
}
