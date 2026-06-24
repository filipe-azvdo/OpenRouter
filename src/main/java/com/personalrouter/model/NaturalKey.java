package com.personalrouter.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record NaturalKey(String rodovia, BigDecimal kmM, String sentido) {

    public NaturalKey {
        kmM = kmM == null ? null : kmM.setScale(3, RoundingMode.HALF_UP);
    }
}
