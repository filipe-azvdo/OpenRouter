package com.personalrouter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "toll_plaza")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TollPlaza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String concessionaria;

    private String nome;

    @Column(name = "ano_pnv_snv")
    private Integer anoPnvSnv;

    @Column(nullable = false)
    private String rodovia;

    private String uf;

    @Column(name = "km_m", nullable = false)
    private BigDecimal kmM;

    private String municipio;

    @Column(name = "tipo_pista")
    private String tipoPista;

    @Column(nullable = false)
    private String sentido;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NaturalKey naturalKey() {
        return new NaturalKey(rodovia, kmM, sentido);
    }
}
