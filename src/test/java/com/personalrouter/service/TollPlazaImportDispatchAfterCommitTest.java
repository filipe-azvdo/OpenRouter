package com.personalrouter.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.personalrouter.support.AbstractPersistenceTest;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "ors.api.key=test-key")
class TollPlazaImportDispatchAfterCommitTest extends AbstractPersistenceTest {

    @Autowired private TollPlazaImportService service;
    @Autowired private PlatformTransactionManager txManager;
    @MockitoBean private TollPlazaImportWorker worker;

    @Test
    void workerIsDispatchedOnlyAfterCommit() throws IOException {
        byte[] csv = loadCsv("csv/two-plazas.csv");
        TransactionTemplate tx = new TransactionTemplate(txManager);

        tx.executeWithoutResult(status -> {
            service.importCsv(csv);
            verify(worker, never()).process(any(), any());
        });

        verify(worker, times(1)).process(any(), any());
    }

    private byte[] loadCsv(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            assert is != null : "Test resource not found: " + path;
            return is.readAllBytes();
        }
    }
}
