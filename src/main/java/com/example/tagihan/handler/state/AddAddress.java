package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@StateHandler(state = State.ADD_ADDRESS)
@Slf4j
@RequiredArgsConstructor
public class AddAddress implements StateHandlers {

    private static final int MIN_ADDRESS_LENGTH = 9;
    private static final int MAX_ADDRESS_LENGTH = 500;

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

            if (text == null || text.isBlank()) {
                log.warn("Empty address input from user: {}", jid);
                return sendValidationError(chatId, "Alamat tidak boleh kosong. Silahkan masukkan alamat lengkap.");
            }

            String address = text.trim();

            if (address.length() < MIN_ADDRESS_LENGTH) {
                log.warn("Address too short from user: {} (length: {})", jid, address.length());
                return sendValidationError(chatId,
                        String.format("Alamat terlalu pendek. Minimal %d karakter. Silahkan masukkan alamat yang lebih lengkap.",
                                MIN_ADDRESS_LENGTH));
            }

            if (address.length() > MAX_ADDRESS_LENGTH) {
                log.warn("Address too long from user: {} (length: {})", jid, address.length());
                return sendValidationError(chatId,
                        String.format("Alamat terlalu panjang. Maksimal %d karakter.", MAX_ADDRESS_LENGTH));
            }

            StateData userState = stateService.getUserState(jid);
            if (userState == null) {
                log.warn("No state found for user: {}", jid);
                return Mono.empty();
            }

            Visit visit = userState.getVisit();
            if (visit == null) {
                log.error("Visit is null for user: {}", jid);
                return Mono.empty();
            }

            visit.setAddress(address);

            // Update state
            return stateService.setVisitData(jid, visit)
                    .doOnSubscribe(sub -> log.info("Processing address for user: {}", jid))
                    .doOnSuccess(state -> log.info("Address added successfully for user: {} - Address: {}",
                            jid, truncateForLog(address)))
                    .doOnError(e -> log.error("Failed to add address for user: {}", jid, e))
                    .then();
        });
    }


    private Mono<Void> sendValidationError(String chatId, String errorMessage) {
        WhatsAppRequestDTO errorRequest = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(errorMessage)
                .build();

        return whatsappService.sendMessageText(errorRequest)
                .doOnSubscribe(sub -> log.info("Sending validation error to {}", chatId))
                .doOnError(e -> log.error("Failed to send validation error", e))
                .then();
    }

    private String truncateForLog(String address) {
        if (address.length() <= 50) {
            return address;
        }
        return address.substring(0, 47) + "...";
    }
}