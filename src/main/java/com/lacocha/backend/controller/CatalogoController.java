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

import com.lacocha.backend.dto.Catalog.PondCreate;
import com.lacocha.backend.dto.Catalog.PondUpdate;
import com.lacocha.backend.dto.Catalog.PondResponse;
import com.lacocha.backend.dto.Catalog.BatchCreate;
import com.lacocha.backend.dto.Catalog.BatchUpdate;
import com.lacocha.backend.dto.Catalog.BatchResponse;
import com.lacocha.backend.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Catálogo")
public class CatalogoController {

    private final CatalogService service;

    public CatalogoController(CatalogService service) {
        this.service = service;
    }

    @GetMapping("/estanques")
    @Operation(summary = "Listar estanques ordenados por nombre")
    public List<PondResponse> listPonds() {
        return service.listPonds();
    }

    @GetMapping("/estanques/{id}")
    @Operation(summary = "Ver un estanque")
    public PondResponse getPond(@PathVariable UUID id) {
        return service.getPond(id);
    }

    @PostMapping("/estanques")
    @Operation(summary = "Crear un estanque desde el panel (el id es opcional)")
    @ResponseStatus(HttpStatus.CREATED)
    public PondResponse createPond(@Valid @RequestBody PondCreate datos) {
        return service.createPond(datos);
    }

    @PatchMapping("/estanques/{id}")
    @Operation(summary = "Editar un estanque: solo cambian los campos enviados")
    public PondResponse updatePond(@PathVariable UUID id, @Valid @RequestBody PondUpdate datos) {
        return service.updatePond(id, datos);
    }

    @GetMapping("/lotes")
    @Operation(summary = "Listar lotes, con filtros opcionales por estanque y estado")
    public List<BatchResponse> listBatches(
            @RequestParam(name = "estanque_id", required = false) UUID pondId,
            @RequestParam(name = "estado", required = false) String status) {
        return service.listBatches(pondId, status);
    }

    @GetMapping("/lotes/{id}")
    @Operation(summary = "Ver un lote")
    public BatchResponse getBatch(@PathVariable UUID id) {
        return service.getBatch(id);
    }

    @PostMapping("/lotes")
    @Operation(summary = "Crear un lote en un estanque existente")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchResponse createBatch(@Valid @RequestBody BatchCreate datos) {
        return service.createBatch(datos);
    }

    @PatchMapping("/lotes/{id}")
    @Operation(summary = "Editar un lote (por ejemplo, cerrarlo con estado = cerrado)")
    public BatchResponse updateBatch(@PathVariable UUID id, @Valid @RequestBody BatchUpdate datos) {
        return service.updateBatch(id, datos);
    }
}
