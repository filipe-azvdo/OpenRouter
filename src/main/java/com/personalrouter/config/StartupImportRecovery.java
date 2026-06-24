package com.personalrouter.config;

import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.repository.TollPlazaImportRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupImportRecovery implements ApplicationRunner {

    private final TollPlazaImportRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        List<TollPlazaImport> orphans =
                repository.findByStatusIn(List.of(ImportStatus.PENDING, ImportStatus.PROCESSING));
        if (orphans.isEmpty()) {
            return;
        }
        for (TollPlazaImport job : orphans) {
            job.setStatus(ImportStatus.FAILED);
            job.setFinishedAt(Instant.now());
        }
        repository.saveAll(orphans);
        log.warn("Recuperação de subida: {} import(s) órfão(s) marcados como FAILED", orphans.size());
    }
}
