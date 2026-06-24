package com.personalrouter.service.csv;

import com.personalrouter.dto.RowError;
import com.personalrouter.exception.InvalidCsvException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

@Component
public class TollPlazaCsvParser {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "concessionaria", "praca_de_pedagio", "ano_do_pnv_snv", "rodovia", "uf", "km_m",
            "municipal", "tipo_de_pista", "sentido", "situacao", "data_da_inativacao",
            "latitude", "longitude");

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setHeader(EXPECTED_HEADERS.toArray(new String[0]))
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .build();

    private String decodeUtf8(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            throw new InvalidCsvException("Arquivo não é UTF-8 válido");
        }
    }

    public void validateStructure(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidCsvException("Arquivo vazio");
        }
        String text = decodeUtf8(content);
        int newline = text.indexOf('\n');
        String headerLine = (newline < 0 ? text : text.substring(0, newline)).strip();
        List<String> headers = List.of(headerLine.split(";", -1));
        if (!headers.equals(EXPECTED_HEADERS)) {
            throw new InvalidCsvException(
                    "Cabeçalho fora do padrão; esperadas 13 colunas: " + EXPECTED_HEADERS);
        }
        String rest = newline < 0 ? "" : text.substring(newline + 1);
        if (rest.isBlank()) {
            throw new InvalidCsvException("Arquivo sem linhas de dados");
        }
    }

    public CsvParseResult parse(byte[] content) {
        String text = decodeUtf8(content);
        List<TollPlazaCsvRow> rows = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        int totalRows = 0;
        try (CSVParser csv = CSVParser.parse(new StringReader(text), FORMAT)) {
            for (CSVRecord record : csv) {
                totalRows++;
                long line = record.getRecordNumber();
                try {
                    rows.add(toRow(record));
                } catch (RuntimeException e) {
                    errors.add(new RowError(line, e.getMessage()));
                }
            }
        } catch (java.io.IOException e) {
            throw new InvalidCsvException("Falha ao ler o CSV: " + e.getMessage());
        }
        return new CsvParseResult(rows, errors, totalRows);
    }

    private TollPlazaCsvRow toRow(CSVRecord r) {
        String rodovia = required(r.get("rodovia"), "rodovia");
        String sentido = required(r.get("sentido"), "sentido");
        BigDecimal kmM = parseKm(required(r.get("km_m"), "km_m"));
        double latitude = parseDouble(required(r.get("latitude"), "latitude"), "latitude");
        double longitude = parseDouble(required(r.get("longitude"), "longitude"), "longitude");
        Integer ano = parseOptionalInt(r.get("ano_do_pnv_snv"));
        return new TollPlazaCsvRow(
                blankToNull(r.get("concessionaria")),
                blankToNull(r.get("praca_de_pedagio")),
                ano,
                rodovia,
                blankToNull(r.get("uf")),
                kmM,
                blankToNull(r.get("municipal")),
                blankToNull(r.get("tipo_de_pista")),
                sentido,
                latitude,
                longitude);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente: " + field);
        }
        return value.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static BigDecimal parseKm(String value) {
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("km_m inválido: " + value);
        }
    }

    private static double parseDouble(String value, String field) {
        try {
            return Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " inválido: " + value);
        }
    }

    private static Integer parseOptionalInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ano_do_pnv_snv inválido: " + value);
        }
    }
}
