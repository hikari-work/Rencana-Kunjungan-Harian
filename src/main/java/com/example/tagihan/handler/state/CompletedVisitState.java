package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@StateHandler(state = State.COMPLETED)
public class CompletedVisitState implements StateHandlers {
    private final StateService stateService;
    private final VisitService visitService;
    @Transient
    private final WhatsappService whatsappService;

    public CompletedVisitState(StateService stateService, VisitService visitService, WhatsappService whatsappService) {
        this.stateService = stateService;
        this.visitService = visitService;
        this.whatsappService = whatsappService;
    }

    @Override
    public Mono<Void> handle(StateData stateData) {
        String jid = stateData.getVisit().getUserId();
        log.info("Processing completed visit for user: {}", jid);

        return visitService.save(stateData.getVisit())
                .doOnSuccess(visit -> {
                    log.info("Visit saved successfully: {}", visit);
                    stateService.removeState(jid);
                })
                .flatMap(visit -> {
                    WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                            .phone(jid)
                            .message("Selamat! Tagihan sudah selesai disimpan")
                            .type(WhatsAppMessageType.TEXT)
                            .build();

                    return whatsappService.sendMessage(request);
                })
                .doOnError(error -> log.error("Failed to complete visit for user: {}", jid, error))
                .onErrorResume(error -> {
                    WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                            .phone(jid)
                            .message("Maaf, terjadi kesalahan saat menyimpan tagihan. Silakan coba lagi.")
                            .type(WhatsAppMessageType.TEXT)
                            .build();

                    return whatsappService.sendMessage(request);
                })
                .then();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return Mono.empty();
    }
}