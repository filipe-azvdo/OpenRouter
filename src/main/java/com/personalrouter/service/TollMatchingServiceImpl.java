package com.personalrouter.service;

import com.personalrouter.dto.Coordinate;
import com.personalrouter.dto.TollPlazaDto;
import com.personalrouter.model.TollPlaza;
import com.personalrouter.repository.TollPlazaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TollMatchingServiceImpl implements TollMatchingService {

    private static final double BUFFER_METERS = 500.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double BUFFER_DEGREES = BUFFER_METERS / 111_320.0;

    private final TollPlazaRepository tollPlazaRepository;

    @Override
    public List<TollPlazaDto> findTollPlazasAlongRoute(String encodedPolyline) {
        List<Coordinate> routePoints = PolylineDecoder.decode(encodedPolyline);
        if (routePoints.isEmpty()) {
            return List.of();
        }

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        for (Coordinate c : routePoints) {
            minLat = Math.min(minLat, c.lat());
            maxLat = Math.max(maxLat, c.lat());
            minLon = Math.min(minLon, c.lon());
            maxLon = Math.max(maxLon, c.lon());
        }

        List<TollPlaza> candidates = tollPlazaRepository.findActiveWithinBbox(
                minLat - BUFFER_DEGREES, maxLat + BUFFER_DEGREES,
                minLon - BUFFER_DEGREES, maxLon + BUFFER_DEGREES);

        List<MatchedPlaza> matched = new ArrayList<>();
        for (TollPlaza plaza : candidates) {
            double bestDistance = Double.MAX_VALUE;
            int bestSegmentIndex = -1;
            for (int i = 0; i < routePoints.size() - 1; i++) {
                double d = distanceToSegment(
                        plaza.getLatitude(), plaza.getLongitude(),
                        routePoints.get(i), routePoints.get(i + 1));
                if (d < bestDistance) {
                    bestDistance = d;
                    bestSegmentIndex = i;
                }
            }
            if (bestDistance <= BUFFER_METERS) {
                matched.add(new MatchedPlaza(plaza, bestSegmentIndex, bestDistance));
            }
        }

        matched.sort(Comparator.comparingInt(MatchedPlaza::segmentIndex)
                .thenComparingDouble(MatchedPlaza::distance));

        return matched.stream().map(m -> toDto(m.plaza())).toList();
    }

    private static double distanceToSegment(double pLat, double pLon,
                                            Coordinate segA, Coordinate segB) {
        Coordinate nearest = nearestPointOnSegment(pLat, pLon, segA, segB);
        return haversine(pLat, pLon, nearest.lat(), nearest.lon());
    }

    private static Coordinate nearestPointOnSegment(double pLat, double pLon,
                                                    Coordinate a, Coordinate b) {
        double dx = b.lon() - a.lon();
        double dy = b.lat() - a.lat();
        if (dx == 0 && dy == 0) {
            return a;
        }
        double t = ((pLon - a.lon()) * dx + (pLat - a.lat()) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        return new Coordinate(a.lat() + t * dy, a.lon() + t * dx);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static TollPlazaDto toDto(TollPlaza plaza) {
        return new TollPlazaDto(
                plaza.getNome(),
                plaza.getConcessionaria(),
                plaza.getRodovia(),
                plaza.getUf(),
                plaza.getKmM(),
                plaza.getSentido(),
                plaza.getLatitude(),
                plaza.getLongitude());
    }

    private record MatchedPlaza(TollPlaza plaza, int segmentIndex, double distance) {}
}
