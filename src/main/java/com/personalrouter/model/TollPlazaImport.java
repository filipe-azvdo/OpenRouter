package com.personalrouter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "toll_plaza_import")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TollPlazaImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content_hash", nullable = false, unique = true)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status;

    private Integer inserted;

    private Integer reactivated;

    private Integer updated;

    private Integer deactivated;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(columnDefinition = "TEXT")
    private String errors;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
