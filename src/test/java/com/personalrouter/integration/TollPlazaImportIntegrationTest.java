package com.personalrouter.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalrouter.repository.TollPlazaImportRepository;
import com.personalrouter.repository.TollPlazaRepository;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "ors.api.key=test-key")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TollPlazaImportIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean("tollImportExecutor")
        Executor tollImportExecutor() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TollPlazaRepository plazaRepository;
    @Autowired private TollPlazaImportRepository importRepository;

    @BeforeEach
    void clean() {
        importRepository.deleteAll();
        plazaRepository.deleteAll();
    }

    private MockMultipartFile file(String resource) throws Exception {
        byte[] bytes = new ClassPathResource(resource).getInputStream().readAllBytes();
        return new MockMultipartFile("file", "f.csv", "text/csv", bytes);
    }

    private String uploadExpectingAccepted(String resource) throws Exception {
        String json = mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(file(resource)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("importId").asText();
    }

    @Test
    void uploadValido_processaComSucesso_eReportaLinhaInvalida() throws Exception {
        String id = uploadExpectingAccepted("csv/three-plazas.csv");

        mockMvc.perform(get("/api/v1/toll-plazas/imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.inserted").value(2))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(3));
    }

    @Test
    void reuploadMesmoArquivo_eIdempotente_naoReprocessa() throws Exception {
        uploadExpectingAccepted("csv/three-plazas.csv");

        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(file("csv/three-plazas.csv")))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(importRepository.count()).isEqualTo(1);
    }

    @Test
    void reuploadSemPraca_softDelete_eReintroducao_reativa() throws Exception {
        uploadExpectingAccepted("csv/three-plazas.csv");

        uploadExpectingAccepted("csv/two-plazas.csv");
        org.assertj.core.api.Assertions.assertThat(
                plazaRepository.findAll()).filteredOn(p -> !p.isActive()).hasSize(1);

        uploadExpectingAccepted("csv/three-plazas.csv");
        org.assertj.core.api.Assertions.assertThat(
                plazaRepository.findAll()).filteredOn(p -> p.isActive()).hasSize(2);
    }

    @Test
    void cabecalhoForaDoPadrao_retorna400_semEscrita() throws Exception {
        MockMultipartFile bad = new MockMultipartFile("file", "bad.csv", "text/csv",
                "a;b;c\n1;2;3".getBytes());
        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(bad))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(importRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(plazaRepository.count()).isZero();
    }

    @Test
    void arquivoNaoUtf8_retorna400() throws Exception {
        byte[] invalidUtf8 = new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41, 0x42};
        MockMultipartFile bad = new MockMultipartFile("file", "latin1.csv", "text/csv", invalidUtf8);
        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(bad))
                .andExpect(status().isBadRequest());
    }
}
