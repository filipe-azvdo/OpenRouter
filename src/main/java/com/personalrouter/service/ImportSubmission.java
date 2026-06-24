package com.personalrouter.service;

import com.personalrouter.dto.TollPlazaImportResultDto;

public record ImportSubmission(TollPlazaImportResultDto result, boolean created) {
}
