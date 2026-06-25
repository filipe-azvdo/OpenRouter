package com.personalrouter.service;

import com.personalrouter.service.csv.TollPlazaCsvRow;
import java.util.List;

/** Reconcilia praças de pedágio a partir de linhas CSV importadas. */
public interface TollPlazaReconciliationService {

    /** Resultado agregado da reconciliação. */
    record ReconciliationCounts(int inserted, int reactivated, int updated, int deactivated) {
    }

    /** Executa a reconciliação (insert / reactivate / update / deactivate). */
    ReconciliationCounts reconcile(List<TollPlazaCsvRow> rows);
}
