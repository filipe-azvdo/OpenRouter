package com.personalrouter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.support.AbstractPersistenceTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TollPlazaImportRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    private TollPlazaImportRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    private static TollPlazaImport job(String hash, ImportStatus status) {
        return TollPlazaImport.builder().contentHash(hash).status(status).build();
    }

    @Test
    void findsExistingByHashAndStatusForIdempotency() {
        repository.saveAndFlush(job("abc", ImportStatus.SUCCESS));

        Optional<TollPlazaImport> found = repository.findFirstByContentHashAndStatusIn(
                "abc", List.of(ImportStatus.PENDING, ImportStatus.PROCESSING, ImportStatus.SUCCESS));
        assertThat(found).isPresent();

        Optional<TollPlazaImport> none = repository.findFirstByContentHashAndStatusIn(
                "abc", List.of(ImportStatus.PENDING, ImportStatus.PROCESSING));
        assertThat(none).isEmpty();
    }

    @Test
    void findsOrphansByStatus() {
        repository.saveAndFlush(job("h1", ImportStatus.PENDING));
        repository.saveAndFlush(job("h2", ImportStatus.PROCESSING));
        repository.saveAndFlush(job("h3", ImportStatus.SUCCESS));

        List<TollPlazaImport> orphans = repository.findByStatusIn(
                List.of(ImportStatus.PENDING, ImportStatus.PROCESSING));
        assertThat(orphans).extracting(TollPlazaImport::getContentHash)
                .containsExactlyInAnyOrder("h1", "h2");
    }
}
