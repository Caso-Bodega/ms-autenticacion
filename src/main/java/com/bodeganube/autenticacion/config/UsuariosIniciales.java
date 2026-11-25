package com.bodeganube.autenticacion.config;

import com.bodeganube.autenticacion.model.Rol;
import com.bodeganube.autenticacion.model.Usuario;
import com.bodeganube.autenticacion.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea al arrancar los dos usuarios de demostracion, solo si todavia no existen:
 *  - "operario" (rol OPERARIO): gestiona picking, despacho, inventario y usuarios.
 *  - "tienda1" (rol COMERCIO, comercioId comercio-123): solo ve sus propias ordenes.
 *
 * Como el gateway exige JWT para toda la API, sin estos usuarios nadie podria hacer el primer login.
 * Las contrasenas vienen de variables de entorno (o secretos de Docker Swarm); los valores por defecto
 * son solo para desarrollo local.
 */
@Component
public class UsuariosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UsuariosIniciales.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String passwordOperario;
    private final String passwordComercio;

    public UsuariosIniciales(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                             @Value("${app.usuarios-iniciales.operario-password}") String passwordOperario,
                             @Value("${app.usuarios-iniciales.comercio-password}") String passwordComercio) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordOperario = passwordOperario;
        this.passwordComercio = passwordComercio;
    }

    @Override
    public void run(ApplicationArguments args) {
        crearSiNoExiste("operario", passwordOperario, Rol.OPERARIO, null);
        crearSiNoExiste("tienda1", passwordComercio, Rol.COMERCIO, "comercio-123");
    }

    private void crearSiNoExiste(String username, String password, Rol rol, String comercioId) {
        if (usuarioRepository.existsByUsername(username)) {
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setComercioId(comercioId);
        usuarioRepository.save(usuario);
        log.info("Usuario inicial creado: {} ({})", username, rol);
    }
}
