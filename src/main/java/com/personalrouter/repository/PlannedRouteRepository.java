package com.personalrouter.repository;

import com.personalrouter.model.PlannedRoute;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistência das rotas planejadas salvas. */
public interface PlannedRouteRepository extends JpaRepository<PlannedRoute, UUID> {
}
