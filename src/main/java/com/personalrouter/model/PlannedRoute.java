package com.personalrouter.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Rota planejada salva, com suas paradas como coleção ordenada filha. */
@Entity
@Table(name = "planned_route")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Column(nullable = false)
    private String profile;

    @Column(name = "origin_lat", nullable = false)
    private double originLat;

    @Column(name = "origin_lon", nullable = false)
    private double originLon;

    @Column(name = "origin_label")
    private String originLabel;

    @Column(name = "destination_lat", nullable = false)
    private double destinationLat;

    @Column(name = "destination_lon", nullable = false)
    private double destinationLon;

    @Column(name = "destination_label")
    private String destinationLabel;

    @Column(name = "distance_meters", nullable = false)
    private long distanceMeters;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String geometry;

    @OneToMany(mappedBy = "plannedRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    @Builder.Default
    private List<PlannedRouteStop> stops = new ArrayList<>();

    @OneToMany(mappedBy = "plannedRoute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("matchOrder ASC")
    @Builder.Default
    private List<PlannedRouteToll> tollPlazas = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
