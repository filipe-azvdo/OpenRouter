package com.personalrouter.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

    @Test
    void tollImportExecutorIsSingleThreadedToSerializeImports() {
        ThreadPoolTaskExecutor executor =
                (ThreadPoolTaskExecutor) new AsyncConfig().tollImportExecutor();
        assertThat(executor.getCorePoolSize()).isEqualTo(1);
        assertThat(executor.getMaxPoolSize()).isEqualTo(1);
    }
}
