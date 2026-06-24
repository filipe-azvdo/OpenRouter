package com.personalrouter.service;

import com.personalrouter.model.NaturalKey;
import com.personalrouter.model.TollPlaza;
import com.personalrouter.repository.TollPlazaRepository;
import com.personalrouter.service.csv.TollPlazaCsvRow;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TollPlazaReconciliationService {

    private final TollPlazaRepository repository;

    public record ReconciliationCounts(int inserted, int reactivated, int updated, int deactivated) {
    }

    @Transactional
    public ReconciliationCounts reconcile(List<TollPlazaCsvRow> rows) {
        List<TollPlaza> existing = repository.findAll();
        Map<NaturalKey, TollPlaza> byKey = new HashMap<>();
        for (TollPlaza p : existing) {
            byKey.put(p.naturalKey(), p);
        }

        Set<NaturalKey> seen = new HashSet<>();
        int inserted = 0;
        int reactivated = 0;
        int updated = 0;

        for (TollPlazaCsvRow row : rows) {
            NaturalKey key = row.key();
            boolean firstTime = seen.add(key);
            TollPlaza plaza = byKey.get(key);
            if (plaza == null) {
                plaza = new TollPlaza();
                apply(row, plaza);
                plaza.setActive(true);
                repository.save(plaza);
                byKey.put(key, plaza);
                inserted++;
            } else {
                boolean wasInactive = !plaza.isActive();
                apply(row, plaza);
                plaza.setActive(true);
                repository.save(plaza);
                if (firstTime) {
                    if (wasInactive) {
                        reactivated++;
                    } else {
                        updated++;
                    }
                }
            }
        }

        int deactivated = 0;
        for (TollPlaza p : existing) {
            if (p.isActive() && !seen.contains(p.naturalKey())) {
                p.setActive(false);
                repository.save(p);
                deactivated++;
            }
        }

        log.info("Reconciliação: +{} inseridas, {} reativadas, {} atualizadas, {} desativadas",
                inserted, reactivated, updated, deactivated);
        return new ReconciliationCounts(inserted, reactivated, updated, deactivated);
    }

    private void apply(TollPlazaCsvRow row, TollPlaza plaza) {
        plaza.setConcessionaria(row.concessionaria());
        plaza.setNome(row.nome());
        plaza.setAnoPnvSnv(row.anoPnvSnv());
        plaza.setRodovia(row.rodovia());
        plaza.setUf(row.uf());
        plaza.setKmM(row.kmM());
        plaza.setMunicipio(row.municipio());
        plaza.setTipoPista(row.tipoPista());
        plaza.setSentido(row.sentido());
        plaza.setLatitude(row.latitude());
        plaza.setLongitude(row.longitude());
    }
}
