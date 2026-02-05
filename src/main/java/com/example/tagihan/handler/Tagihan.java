package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dispatcher.StateDispatcher;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Bills;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.service.BillsService;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import com.example.tagihan.util.CurrencyUtil;
import com.example.tagihan.util.DateRangeUtil;
import com.example.tagihan.util.NumberParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;

@Service
@Handler(trigger = "tagihan")
@Slf4j
@RequiredArgsConstructor
public class Tagihan implements Messagehandler {

    private final StateService stateService;
    private final WhatsappService whatsappService;
    private final BillsService billsService;
    private final StateDispatcher stateDispatcher;


    private static final String BILL_NOT_FOUND_ERROR = "BILL_NOT_FOUND";

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String chatId = message.getPayload().getFrom();
        String groupId = message.getPayload().getChatId();
        String text = message.getPayload().getBody().substring(".tagihan".length()).trim();
        String[] parts = text.split("\\s+", 2);

        if (stateService.isUserInState(chatId)) {
            return handleOngoingProcess(chatId, groupId);
        }

        if (parts.length < 1 || parts[0].trim().isEmpty()) {
            return sendErrorMessage(message, "Anda belum mengisi no SPK");
        }

        String spk = parts[0].trim();
        String param1 = parts.length > 1 ? parts[1].trim() : null;

        log.info("Processing tagihan - SPK: '{}', param1: '{}'", spk, param1);

        return processBill(message, chatId, spk, param1, groupId);
    }

    private Mono<Void> handleOngoingProcess(String chatId, String fromId) {
        StateData userState = stateService.getUserState(chatId);
        String spk = userState.getVisit() != null ? userState.getVisit().getName() : "N/A";

        WhatsAppRequestDTO remindRequest = WhatsAppRequestDTO.builder()
                .phone(fromId)
                .message("Anda masih memiliki proses LKN yang belum selesai: " + spk +
                        ". Harap selesaikan terlebih dahulu atau ketik /cancel.")
                .build();

        return whatsappService.sendMessageText(remindRequest)
                .doOnSubscribe(sub -> log.info("Sending ongoing process reminder to {}", chatId))
                .then();
    }

    private Mono<Void> processBill(WebhookPayload message, String fromId, String spk, String param1, String chatId) {
        return billsService.findBillBySpk(spk)
                .doOnSubscribe(sub -> log.info("Searching bill with SPK: '{}'", spk))
                .doOnNext(bill -> log.info("Bill found for SPK: {}", spk))
                .switchIfEmpty(
                        Mono.defer(() -> {
                            log.warn("Bill not found for SPK: '{}'", spk);
                            return sendErrorMessage(message, "No SPK tidak ditemukan")
                                    .then(Mono.error(new BillNotFoundException(BILL_NOT_FOUND_ERROR)));
                        })
                )
                .flatMap(bill -> {
                    LocalDate reminder = DateRangeUtil.parseReminder(param1);
                    Long appointment = NumberParser.parseFirstNumber(param1);
                    if (appointment != null) {
                        if (appointment < 3000) {
                            appointment = null;
                        }
                    }

                    log.info("Processing bill - reminder: {}, appointment: {}", reminder, appointment);

                    return sendBill(fromId, bill, param1, reminder, appointment, chatId);
                })
                .onErrorResume(BillNotFoundException.class, e -> {
                    log.debug("Bill not found error handled for SPK: {}", spk);
                    return Mono.empty();
                })
                .onErrorResume(e -> {
                    log.error("Unexpected error processing bill for SPK: {}", spk, e);
                    return sendErrorMessage(message, "Terjadi kesalahan saat memproses tagihan")
                            .then(Mono.empty());
                });
    }

    private Mono<Void> sendBill(String fromId, Bills bill, String note, LocalDate reminder, Long appointment, String chatId) {
        Visit visit = buildVisit(fromId, bill, note, reminder, appointment);

        return stateService.setVisitData(fromId, visit)
                .doOnNext(state -> log.info("Visit data saved for chatId: {}, next state: {}", fromId, state))
                .flatMap(state -> {
                    log.info("Sending bill notification to chatId: {}", fromId);
                    if (isGroupChat(chatId)) {
                        return sendGroupNotification(chatId, bill)
                                .then(Mono.just(state));
                    }
                    return Mono.just(state);
                })
                .flatMap(state -> stateDispatcher.dispatch(stateService.getUserState(fromId)))
                .doOnSuccess(v -> log.info("Bill processing completed successfully for chatId: {}", fromId))
                .doOnError(e -> log.error("Error processing bill for chatId: {}", fromId, e))
                .then();
    }

    private Visit buildVisit(String chatId, Bills bill, String note, LocalDate reminder, Long appointment) {
        return Visit.builder()
                .spk(bill.getNoSpk())
                .name(bill.getName())
                .note(note)
                .address(bill.getAddress())
                .appointment(appointment)
                .reminderDate(reminder)
                .userId(chatId)
                .debitTray(bill.getDebitTray())
                .penalty(bill.getPenaltyInterest() + bill.getPenaltyPrincipal())
                .interest(bill.getLastInterest())
                .principal(bill.getLastPrincipal())
                .plafond(bill.getPlafond())
                .visitType(VisitType.TAGIHAN)
                .visitDate(Instant.now())
                .imageUrl(null)
                .build();
    }

    private Mono<Void> sendGroupNotification(String chatId, Bills bill) {
        String message = buildGroupMessage(bill);

        WhatsAppRequestDTO requestDTO = WhatsAppRequestDTO.builder()
                .phone(chatId)
                .message(message)
                .build();

        return whatsappService.sendMessageText(requestDTO)
                .doOnSubscribe(sub -> log.info("Sending group notification to {}", chatId))
                .doOnSuccess(response -> log.info("Group notification sent successfully"))
                .doOnError(e -> log.error("Failed to send group notification", e))
                .then();
    }

    private String buildGroupMessage(Bills bill) {
        return String.format(
                "No SPK: %s%n" +
                        "Nama: %s%n" +
                        "Alamat: %s%n" +
                        "Tunggakan: %s%n%n" +
                        "Namun ada data yang belum diisi, ayok japri",
                bill.getNoSpk(),
                bill.getName(),
                bill.getAddress(),
                CurrencyUtil.formatRupiah(bill.getLastInstallment())
        );
    }

    private Mono<Void> sendErrorMessage(WebhookPayload message, String errorMessage) {
        WhatsAppRequestDTO errorRequest = WhatsAppRequestDTO.builder()
                .phone(message.getPayload().getChatId())
                .replyToMessageId(message.getPayload().getId())
                .message(errorMessage)
                .build();

        return whatsappService.sendMessageText(errorRequest)
                .doOnSubscribe(sub -> log.info("Sending error message: {}", errorMessage))
                .then();
    }

    private boolean isGroupChat(String chatId) {
        return chatId != null && chatId.contains("@g.us");
    }

    private static class BillNotFoundException extends RuntimeException {
        public BillNotFoundException(String message) {
            super(message);
        }
    }
}