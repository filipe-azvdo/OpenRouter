package com.personalrouter.service;

import com.personalrouter.dto.TollPlazaDto;
import java.util.List;

/** Localiza praças de pedágio ao longo de uma rota codificada em polyline. */
public interface TollMatchingService {

    /** Retorna as praças de pedágio situadas até 500 m do traçado da rota. */
    List<TollPlazaDto> findTollPlazasAlongRoute(String encodedPolyline);
}
