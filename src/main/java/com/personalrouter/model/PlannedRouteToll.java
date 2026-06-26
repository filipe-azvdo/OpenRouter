package com.personalrouter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Snapshot desnormalizado de uma praça de pedágio casada com uma {@link PlannedRoute}, ordenada por
 * {@code matchOrder}. Persistido uma única vez no create para o read não recalcular o matching
 * geométrico.
 */
@Entity
@Table(name = "planned_route_toll")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannedRouteToll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planned_route_id", nullable = false)
    private PlannedRoute plannedRoute;

    private String nome;

    private String concessionaria;

    @Column(nullable = false)
    private String rodovia;

    private String uf;

    @Column(name = "km_m", nullable = false)
    private BigDecimal kmM;

    @Column(nullable = false)
    private String sentido;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "match_order", nullable = false)
    private int matchOrder;
}
