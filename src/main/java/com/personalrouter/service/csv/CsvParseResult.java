package com.personalrouter.service.csv;

import com.personalrouter.dto.RowError;
import java.util.List;

public record CsvParseResult(List<TollPlazaCsvRow> rows, List<RowError> errors, int totalRows) {
}
