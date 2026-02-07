package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@StateHandler(state = State.ADD_INTERESTED)
@Slf4j
@RequiredArgsConstructor
public class AddInterested implements StateHandlers {
    private final StateService stateService;
    private final WhatsappService whatsappService;

    @Override
    public Mono<Void> handle(StateData stateData) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return Mono.defer(() -> {
            String text = message.getPayload().getBody();
            String jid = message.getPayload().getFrom();
            String chatId = message.getPayload().getChatId();

            StateData userState = stateService.getUserState(jid);
            if (userState == null) {
                log.warn("No state found for user: {}", jid);
                return Mono.empty();
            }

            if (text == null || text.isBlank()) {
                log.warn("Empty interested input by user: {}", jid);
                return sendErrorMessage(chatId, "Pilihan tidak boleh kosong. Ketik nomor 1-4.");
            }

            String interested = parseInterested(text.trim());

            if (interested == null) {
                log.warn("Invalid interested option by user {}: {}", jid, text);
                return sendErrorMessage(chatId,
                        """
                                Pilihan tidak valid. Ketik nomor 1-4:
                                1. Ya, sangat tertarik
                                2. Ya, cukup tertarik
                                3. Belum tertarik
                                4. Tidak tertarik""");
            }

            log.info("Setting interested for user {}: {}", jid, interested);
            userState.getVisit().setInterested(interested);

            return stateService.setVisitData(jid, userState.getVisit())
                    .doOnSuccess(v -> log.info("Interested added successfully for {}", jid))
                    .doOnError(e -> log.error("Failed to add interested for {}", jid, e))
                    .then();
        });
    }

    private String parseInterested(String input) {
        return switch (input) {
            case "1" -> "Ya, sangat tertarik";
            case "2" -> "Ya, cukup tertarik";
            case "3" -> "Belum tertarik";
            case "4" -> "Tidak tertarik";
            default -> null;
        };
    }

    private Mono<Void> sendErrorMessage(String chatId, String errorMessage) {
        WhatsAppRequestDTO errorRequest = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(errorMessage)
                .build();

        return whatsappService.sendMessageText(errorRequest)
                .doOnSubscribe(sub -> log.info("Sending error message: {}", errorMessage))
                .then();
    }
}