package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import com.example.tagihan.util.DateRangeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
@StateHandler(state = State.ADD_REMINDER)
public class AddReminder implements StateHandlers {
    @Transient
    private final WhatsappService whatsappService;
    private final StateService stateService;

    public AddReminder(WhatsappService whatsappService, StateService stateService) {
        this.whatsappService = whatsappService;
        this.stateService = stateService;
    }

    @Override
    public Mono<Void> handle(StateData stateData) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String text = message.getPayload().getBody();
        String jid = message.getPayload().getFrom();

        if (text.equalsIgnoreCase("kosong")) {
            log.info("Empty reminder date input for user: {}, skipping reminder", jid);
            StateData userState = stateService.getUserState(jid);
            userState.getVisit().setReminderDate(LocalDate.now().minusDays(1));
            return stateService.setVisitData(jid, userState.getVisit())
                    .doOnSuccess(v -> log.info("Visit updated without reminder date for {}", jid))
                    .then();
        }

        return Mono.fromCallable(() -> DateRangeUtil.parseDate(text))
                .flatMap(reminderDate -> {
                    log.info("Adding reminder for {}", reminderDate);

                    if (reminderDate.isBefore(LocalDate.now())) {
                        log.warn("Reminder date is in the past, ignoring");
                        WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                                .phone(jid)
                                .message("Tidak Mungkin dong reminder nya kemarin, yok isi lagi")
                                .type(WhatsAppMessageType.TEXT)
                                .build();
                        return whatsappService.sendMessage(request);
                    }

                    StateData userState = stateService.getUserState(jid);
                    userState.getVisit().setReminderDate(reminderDate);
                    return stateService.setVisitData(jid, userState.getVisit())
                            .doOnSuccess(v -> log.info("Reminder date set successfully for {}", jid));
                })
                .onErrorResume(DateTimeParseException.class, e -> {
                    log.error("Failed to parse date: {}", text, e);
                    WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                            .phone(jid)
                            .message("Format tanggal tidak valid. Silakan masukkan tanggal dengan format yang benar (contoh: 2024-12-31 atau 31/12/2024)")
                            .type(WhatsAppMessageType.TEXT)
                            .build();
                    return whatsappService.sendMessage(request);
                })
                .onErrorResume(DateTimeException.class, e -> {
                    log.error("DateTime error for input: {}", text, e);
                    WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                            .phone(jid)
                            .message("Terjadi kesalahan pada tanggal yang dimasukkan. Pastikan tanggal valid (contoh: 31 Februari tidak valid)")
                            .type(WhatsAppMessageType.TEXT)
                            .build();
                    return whatsappService.sendMessage(request);
                })
                .onErrorResume(e -> {
                    log.error("Unexpected error while adding reminder", e);
                    WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                            .phone(jid)
                            .message("Maaf, terjadi kesalahan. Silakan coba lagi")
                            .type(WhatsAppMessageType.TEXT)
                            .build();
                    return whatsappService.sendMessage(request);
                })
                .then();
    }
}