package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.ResponseDTO;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Bills;
import com.example.tagihan.service.BillsService;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Handler(trigger = "tagihan")
@Slf4j
public class Tagihan implements Messagehandler {
    private final StateService stateService;
    private final WhatsappService whatsappService;
    private final BillsService billsService;

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
        if (parts.length <= 2) {
            WhatsAppRequestDTO canceler = WhatsAppRequestDTO.builder()
                    .phone(chatId)
                    .message("Ketik .tagihan untuk menyimpan tagihan Anda.")
                    .build();
            return whatsappService.sendMessageText(canceler)
                    .doOnSubscribe(sub -> log.info("Sending No Part"))
                    .then();
        }
        return billsService.findBillBySpk(parts[1])
                        .flatMap()
                .then();

    }
    private Mono<Void> sendBill(String chatId, Bills bill) {
        stateService.setState(chatId, );
    }
}
