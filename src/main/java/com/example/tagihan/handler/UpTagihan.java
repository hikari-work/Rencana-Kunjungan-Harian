package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Handler;
import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.service.BillsService;
import com.example.tagihan.service.WhatsappService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Handler(trigger = "up_tagihan")
@Component
public class UpTagihan implements Messagehandler {
    private final BillsService billsService;
    private final WhatsappService whatsappService;

    public UpTagihan(BillsService billsService, WhatsappService whatsappService) {
        this.billsService = billsService;
        this.whatsappService = whatsappService;
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        log.info("Up tagihan: {}", message);
        String[] messagePart = message.getPayload().getBody().split(" ");

        if (messagePart.length == 2) {
            return billsService.saveAndDeleteBillsReactive(messagePart[1])
                    .doOnSubscribe(sub -> log.info("Up tagihan: {}", messagePart[1]))
                    .flatMap(result -> {
                        log.info("Update tagihan berhasil untuk: {}", messagePart[1]);
                        WhatsAppRequestDTO success = WhatsAppRequestDTO.builder()
                                .phone(message.getPayload().getChatId())
                                .replyToMessageId(message.getPayload().getId())
                                .message("✅ Berhasil mengupdate data tagihan untuk: " + messagePart[1])
                                .build();
                        return whatsappService.sendMessageText(success);
                    })
                    .onErrorResume(error -> {
                        log.error("Error updating tagihan: ", error);
                        WhatsAppRequestDTO reply = WhatsAppRequestDTO.builder()
                                .phone(message.getPayload().getChatId())
                                .replyToMessageId(message.getPayload().getId())
                                .message("❌ Gagal mengupdate data tagihan. Coba lagi nanti. " + error.getMessage())
                                .build();
                        return whatsappService.sendMessageText(reply);
                    })
                    .then();
        }

        WhatsAppRequestDTO error = WhatsAppRequestDTO.builder()
                .phone(message.getPayload().getChatId())
                .replyToMessageId(message.getPayload().getId())
                .message("⚠️ Format pesan salah. Gunakan: up_tagihan [URL]")
                .build();

        return whatsappService.sendMessageText(error).then();
    }
}