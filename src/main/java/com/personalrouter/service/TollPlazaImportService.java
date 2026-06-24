package com.personalrouter.service;

import com.personalrouter.dto.TollPlazaImportResultDto;
import java.util.UUID;

public interface TollPlazaImportService {

    ImportSubmission importCsv(byte[] content);

    TollPlazaImportResultDto getImport(UUID importId);
}
