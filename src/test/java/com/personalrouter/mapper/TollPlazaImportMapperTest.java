package com.personalrouter.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalrouter.dto.TollPlazaImportResultDto;
import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class TollPlazaImportMapperTest {

    private final TollPlazaImportMapper mapper = Mappers.getMapper(TollPlazaImportMapper.class);

    @Test
    void mapsScalarFieldsAndImportId() {
        UUID id = UUID.randomUUID();
        TollPlazaImport entity = TollPlazaImport.builder()
                .id(id).contentHash("abc").status(ImportStatus.SUCCESS)
                .inserted(5).reactivated(1).updated(2).totalRows(11)
                .errors(null)
                .build();

        TollPlazaImportResultDto dto = mapper.toDto(entity);
        assertThat(dto.importId()).isEqualTo(id);
        assertThat(dto.status()).isEqualTo("SUCCESS");
        assertThat(dto.inserted()).isEqualTo(5);
        assertThat(dto.errors()).isEmpty();
    }

    @Test
    void deserializesErrorsJson() {
        TollPlazaImport entity = TollPlazaImport.builder()
                .id(UUID.randomUUID()).contentHash("abc").status(ImportStatus.SUCCESS)
                .errors("[{\"line\":2,\"reason\":\"latitude inválido: xx\"}]")
                .build();

        TollPlazaImportResultDto dto = mapper.toDto(entity);
        assertThat(dto.errors()).hasSize(1);
        assertThat(dto.errors().get(0).line()).isEqualTo(2L);
        assertThat(dto.errors().get(0).reason()).isEqualTo("latitude inválido: xx");
    }
}
