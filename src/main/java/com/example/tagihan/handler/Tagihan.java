package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.ResponseDTO;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Bills;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.service.BillsService;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import com.example.tagihan.util.NumberParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Handler(trigger = "tagihan")
@Slf4j
public class Tagihan implements Messagehandler {
    private final StateService stateService;
    private final WhatsappService whatsappService;
    private final BillsService billsService;
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public Tagihan(StateService stateService, WhatsappService whatsappService, BillsService billsService) {
        this.stateService = stateService;
        this.whatsappService = whatsappService;
        this.billsService = billsService;
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String chatId = message.getPayload().getFrom();
        String text = message.getPayload().getBody().substring(".tagihan".length()).trim();
        String[] parts = text.split("\\s+", 3);

        if (stateService.isUserInState(chatId)) {
            StateData userState = stateService.getUserState(chatId);

            WhatsAppRequestDTO remindRequest = WhatsAppRequestDTO.builder()
                    .phone(chatId)
                    .message("Anda masih memiliki proses LKN yang belum selesai: " + userState.getVisit().getSpk() +
                            ". Harap selesaikan terlebih dahulu atau ketik /cancel.")
                    .build();

            return whatsappService.sendMessageText(remindRequest)
                    .doOnSubscribe(logs -> log.info("Sending"))
                    .map(ResponseDTO::getMessage)
                    .then();
        }

        Arrays.stream(parts).map(String::trim).forEach(log::info);

        if (parts.length < 1) {
            WhatsAppRequestDTO canceler = WhatsAppRequestDTO.builder()
                    .phone(message.getPayload().getChatId())
                    .replyToMessageId(message.getPayload().getId())
                    .message("Anda belum mengisi no SPK")
                    .build();
            return whatsappService.sendMessageText(canceler)
                    .doOnSubscribe(sub -> log.info("Sending No Part"))
                    .then();
        }

        return billsService.findBillBySpk(parts[0])
                .flatMap(bill -> sendBill(chatId, bill, parts[1], getReminder(parts[1]), NumberParser.parseFirstNumber(parts[1])))
                .switchIfEmpty(
                        Mono.defer(() -> {
                            WhatsAppRequestDTO canceler = WhatsAppRequestDTO.builder()
                                    .phone(message.getPayload().getChatId())
                                    .replyToMessageId(message.getPayload().getId())
                                    .message("No SPK tidak ditemukan")
                                    .build();
                            return whatsappService.sendMessageText(canceler)
                                    .doOnSubscribe(sub -> log.info("SPK Not Found"))
                                    .then();
                        })
                );
    }
    private Mono<Void> sendBill(String chatId, Bills bill, String note, LocalDate reminder, Long appointment) {
        Visit visit = Visit.builder()
                .spk(bill.getNoSpk())
                .name(bill.getName())
                .note(note)
                .address(bill.getAddress())
                .appointment(appointment)
                .reminderDate(reminder)
                .userId(chatId)
                .debitTray(bill.getDebitTray())
                .penalty(bill.getPenaltyInterest() + bill.getPenaltyPrincipal())
                .interest(bill.getInterest())
                .principal(bill.getPrincipal())
                .plafond(bill.getPlafond())
                .visitType(VisitType.TAGIHAN)
                .visitDate(Instant.now())
                .imageUrl(null)
                .build();
        return stateService.setVisitData(chatId, visit)
                .then();

    }

    private LocalDate getReminder(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) return null;
        String date = matcher.group();
        return LocalDate.parse(date);
    }
}
