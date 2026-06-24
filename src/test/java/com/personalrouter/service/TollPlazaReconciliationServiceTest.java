package com.personalrouter.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalrouter.model.TollPlaza;
import com.personalrouter.repository.TollPlazaRepository;
import com.personalrouter.service.TollPlazaReconciliationService.ReconciliationCounts;
import com.personalrouter.service.csv.TollPlazaCsvRow;
import com.personalrouter.support.AbstractPersistenceTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TollPlazaReconciliationService.class)
class TollPlazaReconciliationServiceTest extends AbstractPersistenceTest {

    @Autowired
    private TollPlazaReconciliationService service;

    @Autowired
    private TollPlazaRepository repository;

    private static TollPlazaCsvRow row(String rodovia, String km, String sentido, String nome) {
        return new TollPlazaCsvRow("CONCESS", nome, 2007, rodovia, "MG",
                new BigDecimal(km), "Cidade", "Principal", sentido, -22.6, -46.0);
    }

    @Test
    void firstLoadInsertsAllActive() {
        ReconciliationCounts c = service.reconcile(List.of(
                row("BR-381", "900.9", "Crescente", "A"),
                row("BR-116", "210.0", "Norte", "B")));

        assertThat(c).isEqualTo(new ReconciliationCounts(2, 0, 0, 0));
        assertThat(repository.findAll()).allMatch(TollPlaza::isActive);
    }

    @Test
    void reRunSameContentIsIdempotentAsUpdate() {
        List<TollPlazaCsvRow> rows = List.of(row("BR-381", "900.9", "Crescente", "A"));
        service.reconcile(rows);
        ReconciliationCounts c = service.reconcile(rows);

        assertThat(c).isEqualTo(new ReconciliationCounts(0, 0, 1, 0));
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void absentRowIsSoftDeleted_andReintroducedIsReactivatedWithoutDuplicate() {
        service.reconcile(List.of(
                row("BR-381", "900.9", "Crescente", "A"),
                row("BR-116", "210.0", "Norte", "B")));

        ReconciliationCounts c1 = service.reconcile(List.of(row("BR-381", "900.9", "Crescente", "A")));
        assertThat(c1.deactivated()).isEqualTo(1);
        assertThat(repository.findAll()).filteredOn(p -> !p.isActive()).hasSize(1);

        ReconciliationCounts c2 = service.reconcile(List.of(
                row("BR-381", "900.9", "Crescente", "A"),
                row("BR-116", "210.0", "Norte", "B")));
        assertThat(c2.reactivated()).isEqualTo(1);
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findAll()).allMatch(TollPlaza::isActive);
    }

    @Test
    void duplicateNaturalKeyWithinFileIsLastWinsWithoutCountInflation() {
        ReconciliationCounts c = service.reconcile(List.of(
                row("BR-381", "900.9", "Crescente", "primeiro"),
                row("BR-381", "900.9", "Crescente", "segundo")));

        assertThat(c.inserted()).isEqualTo(1);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().get(0).getNome()).isEqualTo("segundo");
    }
}
