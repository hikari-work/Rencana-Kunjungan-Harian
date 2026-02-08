package com.example.tagihan.handler;

import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockMultipartFile;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public abstract class BaseReportHandler {

    protected static final ZoneId JAKARTA_ZONE = ZoneId.of("Asia/Jakarta");
    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    protected static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    protected static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    protected final WhatsappService whatsappService;

    protected BaseReportHandler(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    protected Mono<Void> sendTextMessage(String jid, String message) {
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
                    log.error("Error sending text message to {}", jid, e);
                    return Mono.empty();
                });
    }

    protected Mono<Void> sendPdfDocument(String jid, byte[] pdfBytes, String accountOfficer, String identifier, String documentType) {
        return Mono.fromCallable(() -> createPdfFile(pdfBytes, identifier, documentType))
                .flatMap(pdfFile -> {
                    WhatsAppRequestDTO request = buildPdfRequest(jid, pdfFile, accountOfficer, documentType);
                    return whatsappService.sendDocument(request);
                })
                .doOnNext(response -> logPdfSendResult(jid, response.getCode(), response.getMessage()))
                .then()
                .onErrorResume(e -> {
                    log.error("Error sending PDF document to {}", jid, e);
                    return Mono.empty();
                });
    }

    private MockMultipartFile createPdfFile(byte[] pdfBytes, String identifier, String documentType) {
        String timestamp = LocalDateTime.now(JAKARTA_ZONE).format(TIMESTAMP_FORMATTER);
        String filename = String.format("%s_%s_%s.pdf", documentType.toUpperCase(), identifier, timestamp);

        return new MockMultipartFile(
                "file",
                filename,
                "application/pdf",
                pdfBytes
        );
    }

    private WhatsAppRequestDTO buildPdfRequest(String jid, MockMultipartFile pdfFile, String accountOfficer, String documentType) {
        String caption = buildPdfCaption(accountOfficer, documentType);

        return WhatsAppRequestDTO.builder()
                .phone(jid)
                .caption(caption)
                .isForwarded(false)
                .multipartFile(pdfFile)
                .type(WhatsAppMessageType.DOCUMENT)
                .build();
    }

    private String buildPdfCaption(String accountOfficer, String documentType) {
        String title = "RKH".equalsIgnoreCase(documentType)
                ? "Rencana Kunjungan Harian"
                : "Laporan Kunjungan Nasabah";

        return String.format("📄 %s\nAO: %s\nTanggal: %s",
                title,
                accountOfficer,
                LocalDateTime.now(JAKARTA_ZONE).format(DATETIME_FORMATTER));
    }

    private void logPdfSendResult(String jid, String code, String message) {
        if ("200".equals(code)) {
            log.info("PDF sent successfully to {}", jid);
        } else {
            log.warn("PDF send failed to {}: {} - {}", jid, code, message);
        }
    }

    protected String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DATE_FORMATTER);
    }

    protected String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATETIME_FORMATTER);
    }
}