package com.lacocha.backend.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.lacocha.backend.service.PisciculturaFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The stream the screens listen on so they find out things without asking.
 *
 * <h2>The browser cannot use EventSource here, and that is on purpose</h2>
 *
 * The obvious way to read this from a browser is {@code new EventSource(url)}. It will not work,
 * and the reason matters: <b>EventSource cannot send headers</b>. This route lives under
 * {@code /api/}, so ApiKeyFilter asks it for the session token in {@code Authorization} or the
 * shared key in {@code X-API-Key}, and EventSource has no way to put either there.
 *
 * The usual workaround is to put the token in the query string, as
 * {@code /api/eventos/stream?token=...}. That was rejected. A session token lasts 180 days, and a
 * URL is the single most copied piece of a request: it lands in the access log of every proxy in
 * between, in Render's own logs, and in anything that keeps browser history. A long-lived
 * credential written into a URL is a credential that leaks somewhere nobody is watching.
 *
 * So the frontend reads this with {@code fetch()} and a ReadableStream, which <em>can</em> set
 * headers, and parses the event stream itself. It costs about thirty lines over there and keeps the
 * token where it belongs, which is in a header.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Eventos", description = "Lo que pasa en el servidor, en vivo")
public class EventStreamController {

    private final PisciculturaFacade facade;

    public EventStreamController(PisciculturaFacade facade) {
        this.facade = facade;
    }

    /**
     * Opens the stream. Without parameters it carries everything; with one it carries only that
     * pond or that batch, which is what the counting screen uses.
     */
    @GetMapping(path = "/eventos/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Recibir en vivo lo que llega al servidor (SSE)",
            description = "Hay que leerlo con fetch() y no con EventSource: la ruta pide el token "
                    + "en una cabecera y EventSource no sabe mandar cabeceras.")
    public SseEmitter stream(
            @RequestParam(name = "estanque_id", required = false) UUID pondId,
            @RequestParam(name = "lote_id", required = false) UUID batchId) {
        return facade.openStream(pondId, batchId);
    }
}
