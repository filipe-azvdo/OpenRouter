package com.personalrouter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import com.personalrouter.dto.TollPlazaDto;
import com.personalrouter.model.TollPlaza;
import com.personalrouter.repository.TollPlazaRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TollMatchingServiceTest {

    @Mock
    private TollPlazaRepository tollPlazaRepository;

    @InjectMocks
    private TollMatchingServiceImpl service;

    @Test
    void findTollPlazasAlongRoute_emptyPolyline_returnsEmpty() {
        assertThat(service.findTollPlazasAlongRoute("")).isEmpty();
        assertThat(service.findTollPlazasAlongRoute(null)).isEmpty();
    }

    @Test
    void findTollPlazasAlongRoute_plazaOnRoute_returnsIt() {
        double baseLat = -23.55;
        double baseLon = -46.63;
        // Short segment where we can place a plaza very close to the line
        String polyline = encodePolyline(List.of(
                new double[]{baseLat, baseLon},
                new double[]{baseLat + 0.10, baseLon + 0.10}
        ));

        // ~50m offset from the midpoint of the segment (well within 500m)
        TollPlaza onRoute = buildPlaza(1L, "Pedagio 1",
                baseLat + 0.05 + 0.0004, baseLon + 0.05);
        // Far away — will be in bbox but beyond 500m
        TollPlaza farAway = buildPlaza(2L, "Pedagio Longe", baseLat + 0.05 + 0.1, baseLon + 0.05);

        when(tollPlazaRepository.findActiveWithinBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(onRoute, farAway));

        List<TollPlazaDto> result = service.findTollPlazasAlongRoute(polyline);

        assertThat(result).extracting(TollPlazaDto::nome).contains("Pedagio 1");
        assertThat(result).extracting(TollPlazaDto::nome).doesNotContain("Pedagio Longe");
    }

    @Test
    void findTollPlazasAlongRoute_noPlazasInBbox_returnsEmpty() {
        String polyline = encodePolyline(List.of(
                new double[]{-23.55, -46.63},
                new double[]{-22.90, -43.17}
        ));

        when(tollPlazaRepository.findActiveWithinBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        assertThat(service.findTollPlazasAlongRoute(polyline)).isEmpty();
    }

    @Test
    void findTollPlazasAlongRoute_plazaWithin500m_included() {
        // Very short route segment; plaza is ~100m from the line
        double baseLat = -23.5505;
        double baseLon = -46.6333;
        String polyline = encodePolyline(List.of(
                new double[]{baseLat, baseLon},
                new double[]{baseLat, baseLon + 0.01}
        ));

        // ~100m offset in latitude (~0.001 degrees)
        TollPlaza close = buildPlaza(1L, "Close", baseLat + 0.001, baseLon + 0.005);

        when(tollPlazaRepository.findActiveWithinBbox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(close));

        List<TollPlazaDto> result = service.findTollPlazasAlongRoute(polyline);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).nome()).isEqualTo("Close");
    }

    private static TollPlaza buildPlaza(Long id, String nome, double lat, double lon) {
        return TollPlaza.builder()
                .id(id)
                .nome(nome)
                .concessionaria("TEST")
                .rodovia("BR-101")
                .uf("SP")
                .kmM(BigDecimal.valueOf(100))
                .sentido("Crescente")
                .latitude(lat)
                .longitude(lon)
                .active(true)
                .build();
    }

    private static String encodePolyline(List<double[]> points) {
        StringBuilder sb = new StringBuilder();
        long prevLat = 0, prevLon = 0;
        for (double[] p : points) {
            long lat = Math.round(p[0] * 1e5);
            long lon = Math.round(p[1] * 1e5);
            sb.append(encodeValue(lat - prevLat));
            sb.append(encodeValue(lon - prevLon));
            prevLat = lat;
            prevLon = lon;
        }
        return sb.toString();
    }

    private static String encodeValue(long value) {
        long v = value < 0 ? ~(value << 1) : (value << 1);
        StringBuilder sb = new StringBuilder();
        while (v >= 0x20) {
            sb.append((char) ((int) ((v & 0x1F) | 0x20) + 63));
            v >>= 5;
        }
        sb.append((char) ((int) v + 63));
        return sb.toString();
    }
}
