package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import org.springframework.data.annotation.Transient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


import java.util.Map;

@Component
@StateHandler(state = State.ADD_INTERESTED)
public class AddInterested implements StateHandlers {

    private static final Map<String, String> acceptableInputs = Map.of("1", "Sangat tertarik", "2", "Tertarik", "3", "Belum Tertarik", "4", "Tidak Tertarik");

    @Transient
    private final WhatsappService whatsappService;
    private final StateService stateService;

    public AddInterested(WhatsappService whatsappService, StateService stateService) {
        this.whatsappService = whatsappService;
        this.stateService = stateService;
    }

    @Override
    public Mono<Void> handle(StateData stateData) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        if (!acceptableInputs.containsKey(message.getPayload().getBody())) {
            WhatsAppRequestDTO requestDTO = WhatsAppRequestDTO.builder()
                    .type(WhatsAppMessageType.TEXT)
                    .phone(message.getPayload().getFrom())
                    .message("Saya tidak dapat menemukan jawaban yang anda kirim silahkan kirim lagi")
                    .build();
            return whatsappService.sendMessage(requestDTO)
                    .then();
        }
        String chatId = message.getPayload().getFrom();
        String answer = acceptableInputs.get(message.getPayload().getBody());
        Visit visit = stateService.getUserState(chatId).getVisit();
        visit.setInterested(answer);
        return stateService.setVisitData(chatId, visit)
                .then();
    }
}
