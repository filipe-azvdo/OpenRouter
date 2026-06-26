package com.personalrouter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalrouter.model.PlannedRoute;
import com.personalrouter.model.PlannedRouteStop;
import com.personalrouter.model.PlannedRouteToll;
import com.personalrouter.support.AbstractPersistenceTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * Valida que a migration Flyway aplica em um PostgreSQL real e bate com as entidades
 * ({@code ddl-auto: validate}), além do round-trip de persistência com paradas ordenadas.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlannedRouteRepositoryTest extends AbstractPersistenceTest {

    @Autowired
    private PlannedRouteRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private static PlannedRoute sampleRoute() {
        PlannedRoute route = PlannedRoute.builder()
                .name("SP -> RJ")
                .profile("driving-car")
                .originLat(-23.5505).originLon(-46.6333).originLabel("São Paulo")
                .destinationLat(-22.9068).destinationLon(-43.1729)
                .destinationLabel("Rio de Janeiro")
                .distanceMeters(430120L).durationSeconds(19800L).geometry("encodedPolyline")
                .build();
        route.getStops().add(PlannedRouteStop.builder()
                .plannedRoute(route).lat(-23.1865).lon(-45.8841).label("SJC").stopOrder(0).build());
        route.getStops().add(PlannedRouteStop.builder()
                .plannedRoute(route).lat(-22.97).lon(-44.30).label("Resende").stopOrder(1).build());
        route.getTollPlazas().add(PlannedRouteToll.builder()
                .plannedRoute(route).nome("Pedágio SJC").concessionaria("Conc1").rodovia("BR-116")
                .uf("SP").kmM(BigDecimal.valueOf(160.500)).sentido("Norte")
                .latitude(-23.20).longitude(-45.90).matchOrder(0).build());
        route.getTollPlazas().add(PlannedRouteToll.builder()
                .plannedRoute(route).nome("Pedágio Resende").concessionaria("Conc2")
                .rodovia("BR-116").uf("RJ").kmM(BigDecimal.valueOf(298.000)).sentido("Norte")
                .latitude(-22.50).longitude(-44.45).matchOrder(1).build());
        return route;
    }

    @Test
    void savesAndReloadsRouteWithStopsInOrder() {
        PlannedRoute saved = repository.saveAndFlush(sampleRoute());
        entityManager.clear();

        PlannedRoute loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getProfile()).isEqualTo("driving-car");
        assertThat(loaded.getGeometry()).isEqualTo("encodedPolyline");
        assertThat(loaded.getStops()).extracting(PlannedRouteStop::getLabel)
                .containsExactly("SJC", "Resende");
        assertThat(loaded.getStops().get(0).getStopOrder()).isZero();
        assertThat(loaded.getTollPlazas()).extracting(PlannedRouteToll::getNome)
                .containsExactly("Pedágio SJC", "Pedágio Resende");
        assertThat(loaded.getTollPlazas().get(0).getMatchOrder()).isZero();
        assertThat(loaded.getTollPlazas().get(0).getKmM()).isEqualByComparingTo("160.500");
    }

    @Test
    void deleteRemovesRouteAndCascadesStopsAndTollPlazas() {
        PlannedRoute saved = repository.saveAndFlush(sampleRoute());
        UUID id = saved.getId();

        repository.deleteById(id);
        repository.flush();
        entityManager.clear();

        assertThat(repository.findById(id)).isEmpty();
        Long remainingStops = entityManager.getEntityManager()
                .createQuery("select count(s) from PlannedRouteStop s", Long.class)
                .getSingleResult();
        assertThat(remainingStops).isZero();
        Long remainingTolls = entityManager.getEntityManager()
                .createQuery("select count(t) from PlannedRouteToll t", Long.class)
                .getSingleResult();
        assertThat(remainingTolls).isZero();
    }
}
