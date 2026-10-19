package com.lacocha.backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Auth.LoginRequest;
import com.lacocha.backend.dto.Auth.RegisterRequest;
import com.lacocha.backend.dto.Auth.SessionResponse;
import com.lacocha.backend.dto.Auth.UserResponse;
import com.lacocha.backend.service.PisciculturaFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {
    private final PisciculturaFacade facade;

    public AuthController(PisciculturaFacade facade) {
        this.facade = facade;
    }
}

