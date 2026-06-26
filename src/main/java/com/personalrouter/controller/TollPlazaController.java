package com.personalrouter.controller;

import com.personalrouter.dto.TollPlazaImportResultDto;
import com.personalrouter.service.ImportSubmission;
import com.personalrouter.service.TollPlazaImportService;
import com.personalrouter.service.TollPlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/api/v1/toll-plazas")
@Tag(name = "Praças de Pedágio", description = "Ingestão da base de praças de pedágio via CSV")
public class TollPlazaController {

    private final TollPlazaImportService importService;
    private final TollPlazaService tollPlazaService;

    public TollPlazaController(
            TollPlazaImportService importService,
            TollPlazaService tollPlazaService) {
        this.importService = importService;
        this.tollPlazaService = tollPlazaService;
    }

    @Operation(summary = "Importa/sincroniza praças via CSV (assíncrono)")
    @ApiResponses({
        @ApiResponse(responseCode = "202",
                description = "Import aceito; processamento assíncrono",
                content = @Content(schema = @Schema(
                        implementation = TollPlazaImportResultDto.class))),
        @ApiResponse(responseCode = "200",
                description = "Arquivo já importado (idempotência)",
                content = @Content(schema = @Schema(
                        implementation = TollPlazaImportResultDto.class))),
        @ApiResponse(responseCode = "400",
                description = "CSV inválido (encoding/cabeçalho/vazio)")
    })
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TollPlazaImportResultDto> importCsv(
            @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Recebido CSV de praças: {} ({} bytes)",
                file.getOriginalFilename(), file.getSize());
        ImportSubmission submission = importService.importCsv(file.getBytes());
        TollPlazaImportResultDto body = submission.result();
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath("/api/v1/toll-plazas/imports/{id}")
                .buildAndExpand(body.importId())
                .toUri();
        HttpStatus statusCode = submission.created() ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(statusCode).location(location).body(body);
    }

    @Operation(summary = "Consulta o status de um import de praças")
    @ApiResponses({
        @ApiResponse(responseCode = "200",
                description = "Status do import",
                content = @Content(schema = @Schema(
                        implementation = TollPlazaImportResultDto.class))),
        @ApiResponse(responseCode = "404",
                description = "Import não encontrado")
    })
    @GetMapping("/imports/{id}")
    public ResponseEntity<TollPlazaImportResultDto> getImport(@PathVariable UUID id) {
        return ResponseEntity.ok(importService.getImport(id));
    }

    @Operation(summary = "Desativa (soft-delete) uma praça de pedágio")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Praça desativada"),
        @ApiResponse(responseCode = "404", description = "Praça não encontrada")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlaza(@PathVariable Long id) {
        tollPlazaService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
