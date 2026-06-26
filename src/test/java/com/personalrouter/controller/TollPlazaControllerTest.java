package com.personalrouter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.personalrouter.dto.TollPlazaImportResultDto;
import com.personalrouter.exception.InvalidCsvException;
import com.personalrouter.exception.TollPlazaImportNotFoundException;
import com.personalrouter.exception.TollPlazaNotFoundException;
import com.personalrouter.service.ImportSubmission;
import com.personalrouter.service.TollPlazaImportService;
import com.personalrouter.service.TollPlazaService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TollPlazaController.class)
class TollPlazaControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TollPlazaImportService importService;
    @MockitoBean private TollPlazaService tollPlazaService;

    private static TollPlazaImportResultDto dto(UUID id, String status) {
        return new TollPlazaImportResultDto(id, status, "hash", null, null, null, null,
                List.of(), null, null);
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "praias.csv", "text/csv", "header\nrow".getBytes());
    }

    @Test
    void uploadNewFileReturns202WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(importService.importCsv(any()))
                .thenReturn(new ImportSubmission(dto(id, "PENDING"), true));

        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(file()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString(
                        "/api/v1/toll-plazas/imports/" + id)))
                .andExpect(jsonPath("$.importId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void uploadDuplicateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(importService.importCsv(any()))
                .thenReturn(new ImportSubmission(dto(id, "SUCCESS"), false));

        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(file()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void uploadInvalidReturns400() throws Exception {
        when(importService.importCsv(any()))
                .thenThrow(new InvalidCsvException("cabeçalho fora do padrão"));

        mockMvc.perform(multipart("/api/v1/toll-plazas/import").file(file()))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void getStatusReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(importService.getImport(id)).thenReturn(dto(id, "SUCCESS"));

        mockMvc.perform(get("/api/v1/toll-plazas/imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importId").value(id.toString()));
    }

    @Test
    void getMissingStatusReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(importService.getImport(id))
                .thenThrow(new TollPlazaImportNotFoundException("não encontrado"));

        mockMvc.perform(get("/api/v1/toll-plazas/imports/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePlazaReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/toll-plazas/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(tollPlazaService).deactivate(1L);
    }

    @Test
    void deleteMissingPlazaReturns404() throws Exception {
        doThrow(new TollPlazaNotFoundException("Praça não encontrada: 99"))
                .when(tollPlazaService).deactivate(99L);

        mockMvc.perform(delete("/api/v1/toll-plazas/{id}", 99L))
                .andExpect(status().isNotFound());
    }
}
