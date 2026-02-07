package com.example.tagihan.service.scheduler;

import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.VisitService;
import com.example.tagihan.service.WhatsappService;
import com.example.tagihan.util.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final VisitService visitService;
    private final WhatsappService whatsappService;

    private static final ZoneId JAKARTA_ZONE = ZoneId.of("Asia/Jakarta");

    @Scheduled(cron = "0 30 7 * * *", zone = "Asia/Jakarta")
    public void sendReminder() {
        LocalDate today = LocalDate.now(JAKARTA_ZONE);
        log.info("Starting reminder scheduler for date: {}", today);

        visitService.findAll()
                .filter(visit -> visit.getReminderDate() != null && visit.getReminderDate().equals(today))
                .collectList()
                .flatMap(visits -> {
                    if (visits.isEmpty()) {
                        log.info("No reminders to send for today");
                        return Mono.empty();
                    }

                    log.info("Found {} visits with reminders for today", visits.size());

                    return Flux.fromIterable(visits)
                            .filter(visit -> visit.getUserId() != null && !visit.getUserId().isBlank())
                            .flatMap(this::sendReminderMessage)
                            .then();
                })
                .doOnSuccess(v -> log.info("Reminder scheduler completed successfully"))
                .doOnError(error -> log.error("Error in reminder scheduler", error))
                .subscribe();
    }

    private Mono<Void> sendReminderMessage(Visit visit) {
        String message = buildReminderMessage(visit);

        WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                .phone(visit.getUserId())
                .message(message)
                .isForwarded(false)
                .type(WhatsAppMessageType.TEXT)
                .build();

        return whatsappService.sendMessageText(request)
                .doOnNext(response -> {
                    if ("200".equals(response.getCode())) {
                        log.info("Reminder sent successfully to {} for visit {}", visit.getUserId(), visit.getName());
                    } else {
                        log.warn("Failed to send reminder to {}: {} - {}",
                                visit.getUserId(), response.getCode(), response.getMessage());
                    }
                })
                .then()
                .onErrorResume(error -> {
                    log.error("Error sending reminder for visit {}", visit.getId(), error);
                    return Mono.empty();
                });
    }

    private String buildReminderMessage(Visit visit) {
        StringBuilder message = new StringBuilder();
        message.append("🔔 *REMINDER KUNJUNGAN HARI INI*\n\n");
        message.append("Nama: ").append(visit.getName() != null ? visit.getName() : "-").append("\n");
        message.append("SPK: ").append(visit.getSpk() != null ? visit.getSpk() : "-").append("\n");
        message.append("Alamat: ").append(visit.getAddress() != null ? visit.getAddress() : "-").append("\n");

        if (visit.getAppointment() != null && visit.getAppointment() > 0) {
            message.append("Janji Bayar:").append(CurrencyUtil.formatRupiah(visit.getAppointment()));
        }

        if (visit.getNote() != null && !visit.getNote().isBlank()) {
            message.append("\nCatatan: ").append(visit.getNote()).append("\n");
        }

        message.append("\n_Jangan lupa kunjungan hari ini!_");

        return message.toString();
    }

}