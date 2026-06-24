package com.personalrouter.repository;

import com.personalrouter.model.TollPlaza;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TollPlazaRepository extends JpaRepository<TollPlaza, Long> {

    @Query("SELECT t FROM TollPlaza t WHERE t.active = true "
            + "AND t.latitude BETWEEN :minLat AND :maxLat "
            + "AND t.longitude BETWEEN :minLon AND :maxLon")
    List<TollPlaza> findActiveWithinBbox(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLon") double minLon,
            @Param("maxLon") double maxLon);
}
