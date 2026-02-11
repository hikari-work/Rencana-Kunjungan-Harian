package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.MessageHandler;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Handler(trigger = "cancel")
@Component
@RequiredArgsConstructor
public class CancelHandler implements MessageHandler {

    private final StateService stateService;
    private final WhatsappService whatsappService;

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String jid = message.getPayload().getFrom();
        StateData userState = stateService.getUserState(jid);
        if (userState == null) {
            return Mono.empty();
        }
        stateService.removeState(jid);
        WhatsAppRequestDTO update = WhatsAppRequestDTO.builder()
                .phone(jid)
                .message("Aksi Dibatalkan")
                .type(WhatsAppMessageType.TEXT)
                .build();
        return whatsappService.sendMessage(update)
                .then();

    }
}
