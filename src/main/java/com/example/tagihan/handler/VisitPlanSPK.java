package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.PdfService;
import com.example.tagihan.service.UserService;
import com.example.tagihan.service.VisitService;
import com.example.tagihan.service.WhatsappService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@Handler(trigger = "lkn")
@RequiredArgsConstructor
public class VisitPlanSPK implements Messagehandler {

    private final VisitService visitService;
    private final UserService userService;
    private final PdfService pdfService;
    private final WhatsappService whatsappService;

    private static final ZoneId JAKARTA_ZONE = ZoneId.of("Asia/Jakarta");

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String jid = message.getPayload().getFrom();
        String body = message.getPayload().getBody().trim().replace(".lkn", "");

        log.info("Processing LKN request from {} with body: {}", jid, body);
        if (body.isBlank()) {
            return sendTextMessage(jid, "Silakan masukkan nomor SPK untuk mencetak LKN.");
        }

        return userService.findByJid(jid)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("User not found for JID: {}", jid);
                    return sendTextMessage(jid, "User tidak ditemukan. Silakan hubungi administrator.")
                            .then(Mono.empty());
                }))
                .flatMap(user -> {
                    log.info("Processing LKN for user: {}, AO: {}", user.getUserId(), user.getAccountOfficer());

                    Flux<Visit> filteredVisits = buildFilteredVisits(body);

                    return filteredVisits
                            .collectList()
                            .flatMap(visits -> {
                                if (visits.isEmpty()) {
                                    log.warn("No visits found for the given criteria");
                                    return sendTextMessage(jid, "Tidak ada data kunjungan yang ditemukan untuk kriteria tersebut.");
                                }

                                log.info("Found {} visits matching criteria", visits.size());

                                return pdfService.generateLKNPdf(
                                                Flux.fromIterable(visits),
                                                user.getAccountOfficer(), "lkn"
                                        )
                                        .flatMap(pdfBytes -> sendPdfDocument(jid, pdfBytes, user.getAccountOfficer()));
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("LKN processed successfully for {}", jid))
                .doOnError(error -> log.error("Error processing LKN for {}", jid, error))
                .onErrorResume(error -> {
                    log.error("Failed to generate LKN for {}", jid, error);
                    return sendTextMessage(jid, "Maaf, terjadi kesalahan: " + error.getMessage());
                })
                .then();
    }

    private Flux<Visit> buildFilteredVisits(String body) {
        Flux<Visit> result = visitService.findAll();

        if (body == null || body.isBlank()) {
            log.info("No filter criteria, using default userId filter");
            return result;
        }

        String trimmedBody = body.trim();

        if (trimmedBody.matches("^\\d{12}$")) {
            log.info("Filtering by SPK: {}", trimmedBody);
            result = result.filter(visit -> trimmedBody.equals(visit.getSpk()));
        }

        return result;
    }

    private Mono<Void> sendPdfDocument(String jid, byte[] pdfBytes, String accountOfficer) {
        return Mono.fromCallable(() -> {
                    String timestamp = LocalDateTime.now(JAKARTA_ZONE)
                            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                    String filename = String.format("LKN_%s_%s.pdf", accountOfficer, timestamp);

                    return new MockMultipartFile(
                            "file",
                            filename,
                            "application/pdf",
                            pdfBytes
                    );
                })
                .flatMap(pdfFile -> {
                    WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                            .phone(jid)
                            .caption("📄 Laporan Kunjungan Nasabah\nAO: " + accountOfficer + "\nTanggal: " +
                                    LocalDateTime.now(JAKARTA_ZONE)
                                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                            .isForwarded(false)
                            .multipartFile(pdfFile)
                            .type(WhatsAppMessageType.DOCUMENT)
                            .build();

                    return whatsappService.sendDocument(request);
                })
                .doOnNext(response -> {
                    if ("200".equals(response.getCode())) {
                        log.info("PDF sent successfully to {}", jid);
                    } else {
                        log.warn("PDF send failed: {} - {}", response.getCode(), response.getMessage());
                    }
                })
                .then()
                .onErrorResume(e -> {
                    log.error("Error sending PDF document", e);
                    return Mono.empty();
                });
    }

    private Mono<Void> sendTextMessage(String jid, String message) {
        WhatsAppRequestDTO request = WhatsAppRequestDTO.builder()
                .phone(jid)
                .message(message)
                .isForwarded(false)
                .type(WhatsAppMessageType.TEXT)
                .build();

        return whatsappService.sendMessageText(request)
                .doOnNext(response -> log.info("Text message sent to {}: {}", jid, response.getMessage()))
                .then()
                .onErrorResume(e -> {
                    log.error("Error sending text message", e);
                    return Mono.empty();
                });
    }
}