package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.MessageHandler;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.PdfService;
import com.example.tagihan.service.VisitService;
import com.example.tagihan.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Slf4j
@Component
@Handler(trigger = "rkh")
public class VisitPlanAccountOfficer extends BaseReportHandler implements MessageHandler {

    private final VisitService visitService;
    private final PdfService pdfService;

    public VisitPlanAccountOfficer(VisitService visitService,
                                   PdfService pdfService,
                                   WhatsappService whatsappService) {
        super(whatsappService);
        this.visitService = visitService;
        this.pdfService = pdfService;
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String jid = message.getPayload().getFrom();
        String chatId = message.getPayload().getChatId();
        LocalDate today = LocalDate.now(JAKARTA_ZONE);

        log.info("Processing RKH request from {} for date: {}", jid, today);

        return getFilteredVisits(today)
                .collectList()
                .flatMap(visits -> {
                    if (visits.isEmpty()) {
                        return handleNoVisitsFound(chatId, today);
                    }
                    return generateAndSendPdf(jid, visits);
                })
                .then()
                .doOnSuccess(v -> log.info("RKH processed successfully for {}", jid))
                .doOnError(error -> log.error("Error processing RKH for {}", jid, error))
                .onErrorResume(error -> handleError(chatId, error))
                .then();
    }

    private Flux<Visit> getFilteredVisits(LocalDate targetDate) {
        return visitService.findAll()
                .filter(visit -> isVisitOnDate(visit, targetDate));
    }

    private boolean isVisitOnDate(Visit visit, LocalDate targetDate) {
        if (visit.getVisitDate() == null) {
            return false;
        }
        LocalDate visitDate = visit.getVisitDate()
                .atZone(JAKARTA_ZONE)
                .toLocalDate();
        return visitDate.equals(targetDate);
    }

    private Mono<Void> handleNoVisitsFound(String chatId, LocalDate date) {
        log.warn("No visits found for date: {}", date);
        String message = String.format("Tidak ada data kunjungan untuk hari ini (%s).",
                date.format(DATE_FORMATTER));
        return sendTextMessage(chatId, message);
    }

    private Mono<Void> generateAndSendPdf(String jid, java.util.List<Visit> visits) {
        log.info("Found {} visits for today", visits.size());

        String accountOfficer = "Cabang Kaligondang";
        String identifier = visits.getFirst().getSpk();

        return pdfService.generateLKNPdf(Flux.fromIterable(visits), accountOfficer, "rkh")
                .flatMap(pdfBytes -> sendPdfDocument(jid, pdfBytes, accountOfficer, identifier, "rkh"));
    }

    private Mono<Void> handleError(String chatId, Throwable error) {
        log.error("Failed to process RKH", error);
        return sendTextMessage(chatId, "Maaf, terjadi kesalahan: " + error.getMessage());
    }
}