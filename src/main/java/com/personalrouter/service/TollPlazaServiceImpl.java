package com.personalrouter.service;

import com.personalrouter.exception.TollPlazaNotFoundException;
import com.personalrouter.model.TollPlaza;
import com.personalrouter.repository.TollPlazaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TollPlazaServiceImpl implements TollPlazaService {

    private final TollPlazaRepository repository;

    @Override
    @Transactional
    public void deactivate(Long id) {
        TollPlaza plaza = repository.findById(id)
                .orElseThrow(() -> new TollPlazaNotFoundException("Praça não encontrada: " + id));
        plaza.setActive(false);
        repository.save(plaza);
    }
}
