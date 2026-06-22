package com.personalrouter.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prova do fallback H2 (KAN-12): com o perfil {@code h2} ativo e sem Docker, o contexto JPA sobe
 * usando H2 em modo PostgreSQL. Subir o contexto significa que o Flyway aplicou
 * {@code V1__create_planned_route.sql} no H2 e que {@code ddl-auto=validate} não acusou divergência
 * de schema — exatamente os critérios de aceite da tarefa. Não depende de Docker, então executa
 * (não pula) nesta máquina.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("h2")
class H2SchemaValidationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationAppliesAndSchemaValidatesOnH2() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).contains("h2:mem");
        }
    }
}
