package com.personalrouter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.repository.TollPlazaImportRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartupImportRecoveryTest {

    @Mock private TollPlazaImportRepository repository;
    @InjectMocks private StartupImportRecovery recovery;

    @Test
    void marksOrphansAsFailedOnStartup() {
        TollPlazaImport orphan = TollPlazaImport.builder()
                .contentHash("h").status(ImportStatus.PROCESSING).build();
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(orphan));

        recovery.run(null);

        assertThat(orphan.getStatus()).isEqualTo(ImportStatus.FAILED);
        org.mockito.Mockito.verify(repository).saveAll(anyList());
    }
}
