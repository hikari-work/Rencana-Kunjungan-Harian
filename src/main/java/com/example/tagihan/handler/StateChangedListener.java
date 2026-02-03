package com.example.tagihan.handler;

import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.event.StateChangedEvent;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class StateChangedListener {

    private final WhatsappService whatsappService;

    public StateChangedListener(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    @EventListener(StateChangedEvent.class)
    public void onStateChanged(StateChangedEvent event) {
        StateData stateData = event.getStateData();

        // Validasi null
        if (stateData == null || stateData.getCurrentState() == null) {
            log.warn("StateData or currentState is null, skipping notification");
            return;
        }

        sendNotification(stateData)
                .doOnError(e -> log.error("Failed to send notification for state: {}",
                        stateData.getCurrentState(), e))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> sendNotification(StateData stateData) {
        State currentState = stateData.getCurrentState();

        return switch (currentState) {
            case REGISTER -> handleRegisterStateUpdate(stateData);
            case ADD_SPK -> handleAddSpkStateUpdate(stateData);
            case ADD_CAPTION -> handleAddCaptionStateUpdate(stateData);
            case ADD_REMINDER -> handleAddReminderStateUpdate(stateData);
            case ADD_LIMIT -> handleAddLimitStateUpdate(stateData);
            case ADD_APPOINTMENT -> handleAddAppointmentStateUpdate(stateData);
        };
    }

    private Mono<Void> handleRegisterStateUpdate(StateData stateData) {
        String chatId = stateData.getVisit().getUserId();
        String name = stateData.getVisit().getName();

        // Validasi null
        if (chatId == null || chatId.isBlank()) {
            log.warn("ChatId is null or blank, skipping REGISTER notification");
            return Mono.empty();
        }

        String message = "Hai!!! Kamu akan simpan data " +
                (name != null ? name : "") +
                " di database tagihan hari ini\n\n" +
                "Tapi Kamu Belum terdaftar pada database user kami, silahkan kirim nama panggilan anda.";

        WhatsAppRequestDTO dto = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(message)
                .build();

        return whatsappService.sendMessage(dto)
                .doOnSubscribe(sub -> log.info("Sending REGISTER message to {}", chatId))
                .doOnSuccess(v -> log.info("REGISTER message sent successfully to {}", chatId))
                .then();
    }

    private Mono<Void> handleAddSpkStateUpdate(StateData stateData) {
        String chatId = stateData.getVisit().getUserId();

        if (chatId == null || chatId.isBlank()) {
            log.warn("ChatId is null or blank, skipping ADD_SPK notification");
            return Mono.empty();
        }

        String name = stateData.getVisit().getName();
        String message = "Silahkan masukkan nomor SPK untuk tagihan " +
                (name != null ? name : "") +
                ".\n\n" +
                "Contoh: SPK/2024/001";

        WhatsAppRequestDTO dto = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(message)
                .build();

        return whatsappService.sendMessage(dto)
                .doOnSubscribe(sub -> log.info("Sending ADD_SPK message to {}", chatId))
                .doOnSuccess(v -> log.info("ADD_SPK message sent successfully to {}", chatId))
                .then();
    }

    private Mono<Void> handleAddCaptionStateUpdate(StateData stateData) {
        String chatId = stateData.getVisit().getUserId();

        if (chatId == null || chatId.isBlank()) {
            log.warn("ChatId is null or blank, skipping ADD_CAPTION notification");
            return Mono.empty();
        }

        String message = """
                Silahkan masukkan caption/keterangan untuk tagihan ini.
                
                Contoh: Pembayaran jasa konsultasi bulan Januari""";

        WhatsAppRequestDTO dto = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(message)
                .build();

        return whatsappService.sendMessage(dto)
                .doOnSubscribe(sub -> log.info("Sending ADD_CAPTION message to {}", chatId))
                .doOnSuccess(v -> log.info("ADD_CAPTION message sent successfully to {}", chatId))
                .then();
    }

    private Mono<Void> handleAddReminderStateUpdate(StateData stateData) {
        String chatId = stateData.getVisit().getUserId();

        if (chatId == null || chatId.isBlank()) {
            log.warn("ChatId is null or blank, skipping ADD_REMINDER notification");
            return Mono.empty();
        }

        String message = """
                Silahkan masukkan tanggal reminder untuk tagihan ini.
                
                Format: DD-MM-YYYY
                Contoh: 25-12-2024""";

        WhatsAppRequestDTO dto = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(message)
                .build();

        return whatsappService.sendMessage(dto)
                .doOnSubscribe(sub -> log.info("Sending ADD_REMINDER message to {}", chatId))
                .doOnSuccess(v -> log.info("ADD_REMINDER message sent successfully to {}", chatId))
                .then();
    }

    private Mono<Void> handleAddLimitStateUpdate(StateData stateData) {
        String chatId = stateData.getVisit().getUserId();

        if (chatId == null || chatId.isBlank()) {
            log.warn("ChatId is null or blank, skipping ADD_LIMIT notification");
            return Mono.empty();
        }

        String message = """
                Silahkan masukkan batas waktu pembayaran (deadline).
                
                Format: DD-MM-YYYY
                Contoh: 31-12-2024""";

        WhatsAppRequestDTO dto = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(message)
                .build();

        return whatsappService.sendMessage(dto)
                .doOnSubscribe(sub -> log.info("Sending ADD_LIMIT message to {}", chatId))
                .doOnSuccess(v -> log.info("ADD_LIMIT message sent successfully to {}", chatId))
                .then();
    }

    private Mono<Void> handleAddAppointmentStateUpdate(StateData stateData) {
        String chatId = stateData.getVisit().getUserId();

        if (chatId == null || chatId.isBlank()) {
            log.warn("ChatId is null or blank, skipping ADD_APPOINTMENT notification");
            return Mono.empty();
        }

        String message = """
                Silahkan masukkan tanggal appointment/pertemuan.
                
                Format: DD-MM-YYYY HH:MM
                Contoh: 15-12-2024 14:00""";

        WhatsAppRequestDTO dto = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(message)
                .build();

        return whatsappService.sendMessage(dto)
                .doOnSubscribe(sub -> log.info("Sending ADD_APPOINTMENT message to {}", chatId))
                .doOnSuccess(v -> log.info("ADD_APPOINTMENT message sent successfully to {}", chatId))
                .then();
    }
}