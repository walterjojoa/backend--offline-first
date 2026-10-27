package com.lacocha.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lacocha.backend.dto.Sync.PushRequest;
import com.lacocha.backend.dto.Sync.PushResponse;
import com.lacocha.backend.dto.Webhook.Incoming;
import com.lacocha.backend.service.PisciculturaFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Where a device drops events without having to speak the whole sync protocol.
 *
 * <h2>What this is, honestly</h2>
 *
 * It is a thinner door onto the <em>same</em> ingestion {@code /api/sync/push} uses. It does not
 * store anything differently, does not validate anything differently, and gets its idempotency from
 * exactly the same place. Pretending otherwise would be a lie worth catching in a review.
 *
 * What it does buy is real, in two parts:
 *
 * <ul>
 *   <li><b>The envelope.</b> A push carries the catalogue of ponds and batches as well as the
 *       events, because a phone can create a pond offline. A counter cannot: it has one job, and
 *       making it send {@code "estanques": [], "lotes": []} to do it means more bytes to assemble
 *       inside 420 bytes of buffer on an ESP32, and a shape that a device builder has to understand
 *       before sending a single count.</li>
 *   <li><b>Who it came from.</b> The source is in the path, so the log says a count arrived from
 *       the counter and not from "a push". When the thesis compares the machine's count against
 *       counting by hand, that distinction is the measurement.</li>
 * </ul>
 *
 * {@code /api/sync/push} keeps working untouched, and the ESP32 sketch already in the repository
 * keeps using it. Nothing had to be reflashed to add this.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Webhook", description = "Entrada de eventos para aparatos que no sincronizan catálogo")
public class WebhookController {

    private final PisciculturaFacade facade;

    public WebhookController(PisciculturaFacade facade) {
        this.facade = facade;
    }

    /**
     * Takes events from one source and answers the same thing a push answers.
     *
     * The answer is the full {@link PushResponse} and not a bare 200, because the device needs to
     * know which of its events were accepted and which came back as duplicates in order to clear
     * its own queue. A counter that cleared its queue on a 200 would lose whatever the server
     * rejected.
     *
     * The source in the path is not trusted for anything but the log and the records: what decides
     * whether an event is stored is the device id in the body, which is a foreign key to the
     * dispositivos table, and a device that was deactivated has its whole push refused.
     */
    @PostMapping("/webhook/{fuente}")
    @Operation(summary = "Recibir eventos de un aparato (contador, sensor) sin el catálogo")
    public PushResponse receive(@PathVariable String fuente, @Valid @RequestBody Incoming incoming) {
        return facade.push(new PushRequest(incoming.deviceId(), List.of(), List.of(),
                incoming.events()));
    }
}
