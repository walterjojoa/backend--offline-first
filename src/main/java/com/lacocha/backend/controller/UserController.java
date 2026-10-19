package com.lacocha.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Auth.TemporaryPassword;
import com.lacocha.backend.dto.Auth.UserResponse;
import com.lacocha.backend.dto.Auth.UserUpdate;
import com.lacocha.backend.service.PisciculturaFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Las personas de la finca. Todo aquí lo hace el productor, no el cuidador. */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Personas")
public class UserController {

    private final PisciculturaFacade facade;

    public UserController(PisciculturaFacade facade) {
        this.facade = facade;
    }

    @GetMapping
    @Operation(summary = "Quiénes tienen cuenta en la finca")
    public List<UserResponse> list(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return facade.listUsers(Tokens.of(authorization));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Dar de baja a alguien o cambiarle el rol")
    public UserResponse update(@RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable UUID id, @Valid @RequestBody UserUpdate data) {
        return facade.updateUser(Tokens.of(authorization), id, data);
    }

    @PostMapping("/{id}/clave-temporal")
    @Operation(summary = "Reiniciar la clave de alguien que la olvidó; la temporal viaja una sola vez")
    public TemporaryPassword resetPassword(
            @RequestHeader(name = "Authorization", required = false) String authorization, @PathVariable UUID id) {
        return facade.resetPassword(Tokens.of(authorization), id);
    }
}
