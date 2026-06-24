package com.personalrouter.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalrouter.model.TollPlaza;
import com.personalrouter.support.AbstractPersistenceTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TollPlazaRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    private TollPlazaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static TollPlaza sample() {
        return TollPlaza.builder()
                .concessionaria("AUTOPISTA FERNÃO DIAS").nome("3 (Cambuí)").anoPnvSnv(2007)
                .rodovia("BR-381").uf("MG").kmM(new BigDecimal("900.900")).municipio("Cambuí")
                .tipoPista("Principal").sentido("Crescente/Decrescente")
                .latitude(-22.628487).longitude(-46.07789).active(true)
                .build();
    }

    @Test
    void savesAndReloadsWithUtf8AndTimestamps() {
        TollPlaza saved = repository.saveAndFlush(sample());
        entityManager.clear();

        TollPlaza loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getConcessionaria()).isEqualTo("AUTOPISTA FERNÃO DIAS");
        assertThat(loaded.getNome()).isEqualTo("3 (Cambuí)");
        assertThat(loaded.getKmM()).isEqualByComparingTo("900.9");
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateNaturalKey() {
        repository.saveAndFlush(sample());
        assertThatThrownBy(() -> repository.saveAndFlush(sample()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
