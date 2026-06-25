package com.personalrouter.service;

import java.util.UUID;

/** Processa um import de praças de pedágio de forma assíncrona. */
public interface TollPlazaImportWorker {

    /** Executa o parse do CSV e a reconciliação, atualizando o status do import. */
    void process(UUID importId, byte[] content);
}
