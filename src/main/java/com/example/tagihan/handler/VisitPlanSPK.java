package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.MessageHandler;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.entity.User;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.service.PdfService;
import com.example.tagihan.service.UserService;
import com.example.tagihan.service.VisitService;
import com.example.tagihan.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Handler(trigger = "lkn")
public class VisitPlanSPK extends BaseReportHandler implements MessageHandler {

    private static final String SPK_PATTERN = "^\\d{12}$";

    @Value("${message.prefix}")
    private String messagePrefix;

    private final VisitService visitService;
    private final UserService userService;
    private final PdfService pdfService;

    public VisitPlanSPK(VisitService visitService,
                        UserService userService,
                        PdfService pdfService,
                        WhatsappService whatsappService) {
        super(whatsappService);
        this.visitService = visitService;
        this.userService = userService;
        this.pdfService = pdfService;
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String jid = message.getPayload().getFrom();
        String body = extractBody(message.getPayload().getBody());
        String chatId = message.getPayload().getChatId();

        log.info("Processing LKN request from {} with body: {}", jid, body);

        if (body.isBlank()) {
            return sendTextMessage(jid, "Silakan masukkan nomor SPK untuk mencetak LKN.");
        }

        return processLknRequest(jid, chatId, body);
    }

    private String extractBody(String rawBody) {
        return rawBody.trim().replace(messagePrefix + "lkn", "").trim();
    }

    private Mono<Void> processLknRequest(String jid, String chatId, String body) {
        return userService.findByJid(jid)
                .switchIfEmpty(handleUserNotFound(chatId))
                .flatMap(user -> {
                    log.info("Processing LKN for user: {}, AO: {}", user.getUserId(), user.getAccountOfficer());
                    return processVisits(chatId, body, user.getAccountOfficer());
                })
                .then()
                .doOnSuccess(v -> log.info("LKN processed successfully for {}", jid))
                .doOnError(error -> log.error("Error processing LKN for {}", jid, error))
                .onErrorResume(error -> handleError(chatId, error))
                .then();
    }

    private Mono<? extends User> handleUserNotFound(String chatId) {
        log.warn("User not found for chatId: {}", chatId);
        return sendTextMessage(chatId, "User tidak ditemukan. Silakan hubungi administrator.")
                .then(Mono.empty());
    }

    private Mono<Void> processVisits(String chatId, String body, String accountOfficer) {
        return buildFilteredVisits(body)
                .filter(visit -> !visit.getVisitType().equals(VisitType.INFORMATIONAL))
                .collectList()
                .flatMap(visits -> {
                    if (visits.isEmpty()) {
                        return handleNoVisitsFound(chatId);
                    }
                    return generateAndSendPdf(chatId, visits, accountOfficer);
                });
    }

    private Flux<Visit> buildFilteredVisits(String body) {
        Flux<Visit> allVisits = visitService.findAll();

        if (body == null || body.isBlank()) {
            log.info("No filter criteria provided");
            return allVisits;
        }

        String trimmedBody = body.trim();
        if (trimmedBody.matches(SPK_PATTERN)) {
            log.info("Filtering by SPK: {}", trimmedBody);
            return allVisits.filter(visit -> trimmedBody.equals(visit.getSpk()));
        }

        return allVisits;
    }

    private Mono<Void> handleNoVisitsFound(String chatId) {
        log.warn("No visits found for the given criteria");
        return sendTextMessage(chatId, "Tidak ada data kunjungan yang ditemukan untuk kriteria tersebut.");
    }

    private Mono<Void> generateAndSendPdf(String chatId, java.util.List<Visit> visits, String accountOfficer) {
        log.info("Found {} visits matching criteria", visits.size());

        String identifier = visits.getFirst().getName();

        return pdfService.generateLKNPdf(Flux.fromIterable(visits), accountOfficer, "lkn")
                .flatMap(pdfBytes -> sendPdfDocument(chatId, pdfBytes, accountOfficer, identifier, "lkn"));
    }

    private Mono<Void> handleError(String chatId, Throwable error) {
        log.error("Failed to generate LKN", error);
        return sendTextMessage(chatId, "Maaf, terjadi kesalahan: " + error.getMessage());
    }
}