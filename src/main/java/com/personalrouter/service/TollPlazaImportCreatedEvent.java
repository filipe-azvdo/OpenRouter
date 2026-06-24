package com.personalrouter.service;

import java.util.UUID;

public record TollPlazaImportCreatedEvent(UUID importId, byte[] content) {}
