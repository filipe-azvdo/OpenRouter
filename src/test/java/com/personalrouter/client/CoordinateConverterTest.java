package com.personalrouter.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.personalrouter.dto.Coordinate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinateConverterTest {

    private final CoordinateConverter converter = new CoordinateConverter();

    @Test
    void toOrsCoordinates_invertsLatLonToLonLat() {
        List<Coordinate> input = List.of(
                new Coordinate(48.8566, 2.3522),
                new Coordinate(51.5074, -0.1278)
        );

        List<List<Double>> result = converter.toOrsCoordinates(input);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).containsExactly(2.3522, 48.8566);
        assertThat(result.get(1)).containsExactly(-0.1278, 51.5074);
    }

    @Test
    void toOrsCoordinates_preservesOrder() {
        List<Coordinate> input = List.of(
                new Coordinate(1.0, 10.0),
                new Coordinate(2.0, 20.0),
                new Coordinate(3.0, 30.0)
        );

        List<List<Double>> result = converter.toOrsCoordinates(input);

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).containsExactly(10.0, 1.0);
        assertThat(result.get(1)).containsExactly(20.0, 2.0);
        assertThat(result.get(2)).containsExactly(30.0, 3.0);
    }

    @Test
    void toOrsCoordinates_withNegativeCoordinates() {
        List<Coordinate> input = List.of(new Coordinate(-33.8688, 151.2093));

        List<List<Double>> result = converter.toOrsCoordinates(input);

        assertThat(result.get(0)).containsExactly(151.2093, -33.8688);
    }

    @Test
    void toOrsCoordinates_withSinglePoint() {
        List<Coordinate> input = List.of(new Coordinate(0.0, 0.0));

        List<List<Double>> result = converter.toOrsCoordinates(input);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsExactly(0.0, 0.0);
    }
}
