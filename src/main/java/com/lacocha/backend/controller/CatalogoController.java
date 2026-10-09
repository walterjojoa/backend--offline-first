package com.lacocha.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Catalogo.EstanqueCrear;
import com.lacocha.backend.dto.Catalogo.EstanqueEditar;
import com.lacocha.backend.dto.Catalogo.EstanqueSalida;
import com.lacocha.backend.dto.Catalogo.LoteCrear;
import com.lacocha.backend.dto.Catalogo.LoteEditar;
import com.lacocha.backend.dto.Catalogo.LoteSalida;
import com.lacocha.backend.service.CatalogoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Catálogo")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping("/estanques")
    @Operation(summary = "Listar estanques ordenados por nombre")
    public List<EstanqueSalida> listarEstanques() {
        return service.listarEstanques();
    }

    @GetMapping("/estanques/{id}")
    @Operation(summary = "Ver un estanque")
    public EstanqueSalida obtenerEstanque(@PathVariable UUID id) {
        return service.obtenerEstanque(id);
    }

    @PostMapping("/estanques")
    @Operation(summary = "Crear un estanque desde el panel (el id es opcional)")
    @ResponseStatus(HttpStatus.CREATED)
    public EstanqueSalida crearEstanque(@Valid @RequestBody EstanqueCrear datos) {
        return service.crearEstanque(datos);
    }

    @PatchMapping("/estanques/{id}")
    @Operation(summary = "Editar un estanque: solo cambian los campos enviados")
    public EstanqueSalida editarEstanque(@PathVariable UUID id, @Valid @RequestBody EstanqueEditar datos) {
        return service.editarEstanque(id, datos);
    }

    @GetMapping("/lotes")
    @Operation(summary = "Listar lotes, con filtros opcionales por estanque y estado")
    public List<LoteSalida> listarLotes(
            @RequestParam(name = "estanque_id", required = false) UUID estanqueId,
            @RequestParam(name = "estado", required = false) String estado) {
        return service.listarLotes(estanqueId, estado);
    }

    @GetMapping("/lotes/{id}")
    @Operation(summary = "Ver un lote")
    public LoteSalida obtenerLote(@PathVariable UUID id) {
        return service.obtenerLote(id);
    }

    @PostMapping("/lotes")
    @Operation(summary = "Crear un lote en un estanque existente")
    @ResponseStatus(HttpStatus.CREATED)
    public LoteSalida crearLote(@Valid @RequestBody LoteCrear datos) {
        return service.crearLote(datos);
    }

    @PatchMapping("/lotes/{id}")
    @Operation(summary = "Editar un lote (por ejemplo, cerrarlo con estado = cerrado)")
    public LoteSalida editarLote(@PathVariable UUID id, @Valid @RequestBody LoteEditar datos) {
        return service.editarLote(id, datos);
    }
}
