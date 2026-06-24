package com.personalrouter.repository;

import com.personalrouter.model.ImportStatus;
import com.personalrouter.model.TollPlazaImport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TollPlazaImportRepository extends JpaRepository<TollPlazaImport, UUID> {

    Optional<TollPlazaImport> findFirstByContentHashAndStatusIn(
            String contentHash, Collection<ImportStatus> statuses);

    List<TollPlazaImport> findByStatusIn(Collection<ImportStatus> statuses);
}
