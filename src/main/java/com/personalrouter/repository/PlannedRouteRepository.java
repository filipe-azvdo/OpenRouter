package com.personalrouter.repository;

import com.personalrouter.model.PlannedRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistência das rotas planejadas salvas. */
public interface PlannedRouteRepository extends JpaRepository<PlannedRoute, UUID> {
}
