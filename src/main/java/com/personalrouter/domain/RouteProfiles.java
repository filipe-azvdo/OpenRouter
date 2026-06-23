package com.personalrouter.domain;

import java.util.Set;

public final class RouteProfiles {
    public static final String DRIVING_CAR = "driving-car";
    public static final String DRIVING_HGV = "driving-hgv";
    public static final String DEFAULT = DRIVING_CAR;
    public static final String PATTERN = DRIVING_CAR + "|" + DRIVING_HGV;
    public static final Set<String> SUPPORTED = Set.of(DRIVING_CAR, DRIVING_HGV);
    public static final String UNSUPPORTED_MESSAGE =
            "perfil não suportado; use driving-car ou driving-hgv";

    private RouteProfiles() {}
}
