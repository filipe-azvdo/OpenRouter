package com.personalrouter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personalrouter.exception.TollPlazaNotFoundException;
import com.personalrouter.model.TollPlaza;
import com.personalrouter.repository.TollPlazaRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TollPlazaServiceImplTest {

    @Mock
    private TollPlazaRepository repository;

    @InjectMocks
    private TollPlazaServiceImpl service;

    @Test
    void deactivateExistingPlaza() {
        TollPlaza plaza = new TollPlaza();
        plaza.setActive(true);
        when(repository.findById(1L)).thenReturn(Optional.of(plaza));

        service.deactivate(1L);

        assertThat(plaza.isActive()).isFalse();
        verify(repository).save(plaza);
    }

    @Test
    void deactivateMissingPlazaThrows() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(99L))
                .isInstanceOf(TollPlazaNotFoundException.class);
    }
}
