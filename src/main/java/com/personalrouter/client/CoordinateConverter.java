package com.personalrouter.client;

import com.personalrouter.dto.Coordinate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CoordinateConverter {

    public List<List<Double>> toOrsCoordinates(List<Coordinate> points) {
        // ORS segue GeoJSON: [longitude, latitude]
        return points.stream()
                .map(p -> List.of(p.lon(), p.lat()))
                .toList();
    }
}
