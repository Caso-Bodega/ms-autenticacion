package com.bodeganube.autenticacion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bodeganube.autenticacion.model.Rol;
import com.bodeganube.autenticacion.model.Usuario;
import com.bodeganube.autenticacion.repository.UsuarioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuariosInicialesTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Mock
    private UsuarioRepository usuarioRepository;

    @Test
    void conLaBaseVaciaCreaElOperarioYElComercioConSuContrasenaCifrada() {
        when(usuarioRepository.existsByUsername(any())).thenReturn(false);
        UsuariosIniciales iniciales = new UsuariosIniciales(usuarioRepository, passwordEncoder, "clave-operario", "clave-tienda");

        iniciales.run(null);

        ArgumentCaptor<Usuario> guardados = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository, times(2)).save(guardados.capture());
        List<Usuario> usuarios = guardados.getAllValues();
        assertThat(usuarios).extracting(Usuario::getRol).containsExactly(Rol.OPERARIO, Rol.COMERCIO);
        assertThat(usuarios.get(1).getComercioId()).isEqualTo("comercio-123");
        assertThat(passwordEncoder.matches("clave-operario", usuarios.get(0).getPassword())).isTrue();
    }

    @Test
    void siLosUsuariosYaExistenNoLosVuelveACrear() {
        when(usuarioRepository.existsByUsername(any())).thenReturn(true);
        UsuariosIniciales iniciales = new UsuariosIniciales(usuarioRepository, passwordEncoder, "x", "y");

        iniciales.run(null);

        verify(usuarioRepository, never()).save(any());
    }
}
