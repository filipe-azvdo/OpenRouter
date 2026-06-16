package com.personalrouter.client.dto;

import java.util.List;

public record OrsDirectionsRequest(List<List<Double>> coordinates, boolean instructions) {}
