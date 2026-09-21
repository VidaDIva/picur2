package com.picur.demo.service.impl;

import com.picur.demo.DTO.Request.CrearCuentaRequest;
import com.picur.demo.DTO.Request.LoginRequest;
import com.picur.demo.DTO.Respo.CrearCuentaResponse;
import com.picur.demo.exception.ApiException;
import com.picur.demo.mapper.CrearCuentaMapper;
import com.picur.demo.model.CrearCuenta;
import com.picur.demo.repository.CrearCuentaRepository;
import com.picur.demo.service.CrearCuentaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CrearCuentaServiceImpl implements CrearCuentaService {

    private final CrearCuentaRepository repository;
    private final PasswordEncoder passwordEncoder;

    public CrearCuentaServiceImpl(
            CrearCuentaRepository repository,
            PasswordEncoder passwordEncoder) {

        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }


    @Override
    public CrearCuentaResponse crearCuenta(
            CrearCuentaRequest request) {

        // Verificar si el correo ya existe
        if (repository.existsByCorreo(request.correo())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "El correo ya está registrado"
            );
        }

        // Request -> Entity
        CrearCuenta usuario =
                CrearCuentaMapper.toEntity(request);

        // Encriptar contraseña
        usuario.setContrasena(
                passwordEncoder.encode(
                        usuario.getContrasena()
                )
        );

        // Guardar en BD
        CrearCuenta usuarioGuardado =
                repository.save(usuario);

        // Entity -> Response
        return CrearCuentaMapper.dto(
                usuarioGuardado
        );
    }


    @Override
    public CrearCuentaResponse login(LoginRequest request) {

        CrearCuenta usuario =
                repository.findByCorreo(request.correo())
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "Correo no registrado"
                                )
                        );

        // Contraseña incorrecta
        if (!passwordEncoder.matches(
                request.contrasena(),
                usuario.getContrasena()
        )) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Contraseña incorrecta"
            );
        }

        return CrearCuentaMapper.dto(usuario);
    }


    @Override
    public List<CrearCuentaResponse> listarUsuarios() {

        return repository.findAll()
                .stream()
                .map(CrearCuentaMapper::dto)
                .toList();
    }


    @Override
    public CrearCuentaResponse buscarPorId(Long id) {

        CrearCuenta usuario =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "Usuario no encontrado"
                                )
                        );

        return CrearCuentaMapper.dto(usuario);
    }


    @Override
    public CrearCuentaResponse editarCuenta(
            Long id,
            CrearCuentaRequest request) {

        CrearCuenta usuario =
                repository.findById(id)
                        .orElseThrow(() ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "Usuario no encontrado"
                                )
                        );

        usuario.setNombre(request.nombre());
        usuario.setApellido(request.apellido());
        usuario.setCelular(request.celular());
        usuario.setDireccion(request.direccion());
        usuario.setFechaNacimiento(
                request.fechaNacimiento()
        );
        usuario.setCorreo(request.correo());

        // Cambiar contraseña solamente si viene una nueva
        if (request.contrasena() != null
                && !request.contrasena().isBlank()) {

            usuario.setContrasena(
                    passwordEncoder.encode(
                            request.contrasena()
                    )
            );
        }

        CrearCuenta usuarioActualizado =
                repository.save(usuario);

        return CrearCuentaMapper.dto(
                usuarioActualizado
        );
    }


    @Override
    public void eliminarCuenta(Long id) {

        if (!repository.existsById(id)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "Usuario no encontrado"
            );
        }

        repository.deleteById(id);
    }
}
