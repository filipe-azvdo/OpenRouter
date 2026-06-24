package com.personalrouter.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TollPlazaImportResultDto(
        UUID importId,
        String status,
        String contentHash,
        Integer inserted,
        Integer reactivated,
        Integer updated,
        Integer deactivated,
        Integer totalRows,
        List<RowError> errors,
        Instant createdAt,
        Instant finishedAt) {
}
