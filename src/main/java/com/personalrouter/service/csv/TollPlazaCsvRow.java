package com.personalrouter.service.csv;

import com.personalrouter.model.NaturalKey;
import java.math.BigDecimal;

public record TollPlazaCsvRow(
        String concessionaria,
        String nome,
        Integer anoPnvSnv,
        String rodovia,
        String uf,
        BigDecimal kmM,
        String municipio,
        String tipoPista,
        String sentido,
        double latitude,
        double longitude) {

    public NaturalKey key() {
        return new NaturalKey(rodovia, kmM, sentido);
    }
}
