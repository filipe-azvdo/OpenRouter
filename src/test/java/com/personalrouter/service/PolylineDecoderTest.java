package com.personalrouter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.personalrouter.dto.Coordinate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolylineDecoderTest {

    @Test
    void decode_nullOrEmpty_returnsEmptyList() {
        assertThat(PolylineDecoder.decode(null)).isEmpty();
        assertThat(PolylineDecoder.decode("")).isEmpty();
    }

    @Test
    void decode_knownPolyline_returnsExpectedCoordinates() {
        // Encoded polyline for roughly: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        String encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
        List<Coordinate> coords = PolylineDecoder.decode(encoded);

        assertThat(coords).hasSize(3);
        assertThat(coords.get(0).lat()).isCloseTo(38.5, within(0.001));
        assertThat(coords.get(0).lon()).isCloseTo(-120.2, within(0.001));
        assertThat(coords.get(1).lat()).isCloseTo(40.7, within(0.001));
        assertThat(coords.get(1).lon()).isCloseTo(-120.95, within(0.001));
        assertThat(coords.get(2).lat()).isCloseTo(43.252, within(0.001));
        assertThat(coords.get(2).lon()).isCloseTo(-126.453, within(0.001));
    }

    @Test
    void decode_singlePoint_returnsSingleCoordinate() {
        // Encode a single point: lat=-23.5505, lon=-46.6333
        // Manually compute: lat_e5=-2355050, lon_e5=-4663330
        List<Coordinate> result = PolylineDecoder.decode(
                encodePoint(-23.5505, -46.6333));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lat()).isCloseTo(-23.5505, within(0.0001));
        assertThat(result.get(0).lon()).isCloseTo(-46.6333, within(0.0001));
    }

    private static String encodePoint(double lat, double lon) {
        return encodeValue(Math.round(lat * 1e5)) + encodeValue(Math.round(lon * 1e5));
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
