package com.personalrouter.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TollPlazaImportDispatcher {

    private final TollPlazaImportWorker worker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImportCreated(TollPlazaImportCreatedEvent event) {
        worker.process(event.importId(), event.content());
    }
}
