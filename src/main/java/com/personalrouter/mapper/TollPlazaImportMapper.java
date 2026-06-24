package com.personalrouter.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalrouter.dto.RowError;
import com.personalrouter.dto.TollPlazaImportResultDto;
import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface TollPlazaImportMapper {

    ObjectMapper JSON = new ObjectMapper();

    @Mapping(target = "importId", source = "id")
    @Mapping(target = "status", source = "status", qualifiedByName = "statusName")
    @Mapping(target = "errors", source = "errors", qualifiedByName = "jsonToErrors")
    TollPlazaImportResultDto toDto(TollPlazaImport entity);

    @Named("statusName")
    default String statusName(ImportStatus status) {
        return status == null ? null : status.name();
    }

    @Named("jsonToErrors")
    default List<RowError> jsonToErrors(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<RowError>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("errors JSON corrompido", e);
        }
    }
}
