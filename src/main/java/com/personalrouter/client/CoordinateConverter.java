package com.personalrouter.client;

import com.personalrouter.dto.Coordinate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CoordinateConverter {

    public List<List<Double>> toOrsCoordinates(List<Coordinate> points) {
        // ORS segue GeoJSON: [longitude, latitude]
        return points.stream()
                .map(p -> List.of(p.lon(), p.lat()))
                .toList();
    }
}
