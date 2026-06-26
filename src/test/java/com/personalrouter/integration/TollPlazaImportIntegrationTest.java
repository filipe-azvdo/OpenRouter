package com.personalrouter.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import com.personalrouter.repository.TollPlazaImportRepository;
import com.personalrouter.repository.TollPlazaRepository;
import com.personalrouter.service.csv.ContentHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
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

    private void awaitImportDone(String id) {
        await().atMost(5, SECONDS).until(() ->
                importRepository.findById(java.util.UUID.fromString(id))
                        .map(j -> j.getStatus() != ImportStatus.PENDING
                                && j.getStatus() != ImportStatus.PROCESSING)
                        .orElse(false));
    }

    @Test
    void uploadValido_processaComSucesso_eReportaLinhaInvalida() throws Exception {
        String id = uploadExpectingAccepted("csv/three-plazas.csv");
        awaitImportDone(id);

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
        String id = uploadExpectingAccepted("csv/three-plazas.csv");
        awaitImportDone(id);

        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(file("csv/three-plazas.csv")))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(importRepository.count()).isEqualTo(1);
    }

    @Test
    void reuploadSemPraca_naoDesativa() throws Exception {
        String id1 = uploadExpectingAccepted("csv/three-plazas.csv");
        awaitImportDone(id1);

        String id2 = uploadExpectingAccepted("csv/two-plazas.csv");
        awaitImportDone(id2);

        org.assertj.core.api.Assertions.assertThat(
                plazaRepository.findAll()).allMatch(p -> p.isActive());
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
        MockMultipartFile bad = new MockMultipartFile(
                "file", "latin1.csv", "text/csv", invalidUtf8);
        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(bad))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reuploadAposFalha_criaNovoImport_eReprocessa() throws Exception {
        byte[] bytes = new ClassPathResource("csv/three-plazas.csv")
                .getInputStream().readAllBytes();
        String hash = ContentHash.sha256Hex(bytes);

        TollPlazaImport failed = TollPlazaImport.builder()
                .contentHash(hash)
                .status(ImportStatus.FAILED)
                .build();
        importRepository.save(failed);

        String id = uploadExpectingAccepted("csv/three-plazas.csv");
        awaitImportDone(id);

        org.assertj.core.api.Assertions.assertThat(importRepository.count()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/toll-plazas/imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}
