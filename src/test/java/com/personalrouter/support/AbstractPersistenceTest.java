package com.personalrouter.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPersistenceTest {

    private static final boolean DOCKER_AVAILABLE =
            DockerClientFactory.instance().isDockerAvailable();

    private static final PostgreSQLContainer<?> POSTGRES =
            DOCKER_AVAILABLE ? new PostgreSQLContainer<>("postgres:16-alpine") : null;

    static {
        if (DOCKER_AVAILABLE) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (DOCKER_AVAILABLE) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
            registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        } else {
            registry.add("spring.datasource.url",
                    () -> "jdbc:h2:mem:personalrouter;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.datasource.username", () -> "sa");
            registry.add("spring.datasource.password", () -> "");
        }
    }
}
