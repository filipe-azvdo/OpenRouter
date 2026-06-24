package com.personalrouter.service.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.personalrouter.exception.InvalidCsvException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TollPlazaCsvParserTest {

    private final TollPlazaCsvParser parser = new TollPlazaCsvParser();

    private static final String HEADER =
            "concessionaria;praca_de_pedagio;ano_do_pnv_snv;rodovia;uf;km_m;municipal;"
            + "tipo_de_pista;sentido;situacao;data_da_inativacao;latitude;longitude";

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String row(String rodovia, String km, String sentido, String lat, String lon) {
        return "AUTOPISTA FERNÃO DIAS;3 (Cambuí);2007;" + rodovia + ";MG;" + km + ";Cambuí;Principal;"
                + sentido + ";Ativo;;" + lat + ";" + lon;
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> parser.validateStructure(new byte[0]))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void rejectsHeaderOnlyFile() {
        assertThatThrownBy(() -> parser.validateStructure(utf8(HEADER + "\n")))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void rejectsWrongHeader() {
        String bad = "a;b;c;d;e;f;g;h;i;j;k;l;m\n" + row("BR-381", "900.9", "Crescente", "-22.6", "-46.0");
        assertThatThrownBy(() -> parser.validateStructure(utf8(bad)))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] latin1 = "concessionaria".getBytes(StandardCharsets.ISO_8859_1);
        byte[] invalid = new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41};
        assertThatThrownBy(() -> parser.validateStructure(invalid))
                .isInstanceOf(InvalidCsvException.class);
        assertThat(latin1).isNotNull();
    }

    @Test
    void acceptsValidStructure() {
        byte[] content = utf8(HEADER + "\n" + row("BR-381", "900.9", "Crescente", "-22.6", "-46.0"));
        parser.validateStructure(content);
    }

    @Test
    void parsesValidRowsAndNormalizesKm() {
        byte[] content = utf8(HEADER + "\n" + row("BR-381", "900.9", "Crescente", "-22.628487", "-46.07789"));
        CsvParseResult result = parser.parse(content);

        assertThat(result.totalRows()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).hasSize(1);
        TollPlazaCsvRow r = result.rows().get(0);
        assertThat(r.rodovia()).isEqualTo("BR-381");
        assertThat(r.kmM()).isEqualByComparingTo("900.9");
        assertThat(r.latitude()).isEqualTo(-22.628487);
        assertThat(r.nome()).isEqualTo("3 (Cambuí)");
    }

    @Test
    void collectsRowErrorWithoutAbortingBatch() {
        String badLatLon = row("BR-381", "900.9", "Crescente", "xx", "yy");
        String good = row("BR-116", "210.0", "Norte", "-23.0", "-46.5");
        byte[] content = utf8(HEADER + "\n" + badLatLon + "\n" + good);

        CsvParseResult result = parser.parse(content);
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).rodovia()).isEqualTo("BR-116");
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).line()).isEqualTo(1L);
    }

    @Test
    void rowMissingNaturalKeyIsAnError() {
        String missingRodovia = row("", "900.9", "Crescente", "-22.6", "-46.0");
        CsvParseResult result = parser.parse(utf8(HEADER + "\n" + missingRodovia));
        assertThat(result.rows()).isEmpty();
        assertThat(result.errors()).hasSize(1);
    }
}
