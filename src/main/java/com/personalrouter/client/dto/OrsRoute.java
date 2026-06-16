package com.personalrouter.client.dto;

import java.util.List;

public record OrsRoute(OrsRouteSummary summary, String geometry, List<OrsSegment> segments) {}
