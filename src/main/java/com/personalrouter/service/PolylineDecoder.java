package com.personalrouter.service;

import com.personalrouter.dto.Coordinate;
import java.util.ArrayList;
import java.util.List;

public final class PolylineDecoder {

    private PolylineDecoder() {}

    public static List<Coordinate> decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return List.of();
        }

        List<Coordinate> coordinates = new ArrayList<>();
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lon = 0;

        while (index < len) {
            int shift = 0;
            int result = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1F) << shift;
                shift += 5;
            } while (b >= 0x20);
            lon += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            coordinates.add(new Coordinate(lat / 1e5, lon / 1e5));
        }
        return coordinates;
    }
}
