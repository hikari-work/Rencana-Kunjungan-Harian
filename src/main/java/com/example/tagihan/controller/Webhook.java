package com.example.tagihan.controller;

import com.example.tagihan.dispatcher.WhatsAppMessageDispatcher;
import com.example.tagihan.dto.WebhookData;
import com.example.tagihan.dto.WebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RestController
public class Webhook {

    private final WhatsAppMessageDispatcher whatsAppMessageDispatcher;

    public Webhook(WhatsAppMessageDispatcher whatsAppMessageDispatcher) {
        this.whatsAppMessageDispatcher = whatsAppMessageDispatcher;
    }

    @PostMapping(value = "/webhook", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> webhook(@RequestBody WebhookPayload body) {
        if (body.getEvent().equals("message.ack")) {
            return Mono.just(ResponseEntity.ok("OK"));
        }

        whatsAppMessageDispatcher
                .dispatch(body)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("Error processing webhook: ", error),
                        () -> log.debug("Webhook processed successfully")
                );

        return Mono.just(ResponseEntity.ok("OK"));
    }
    @GetMapping("/")
    public Mono<WebhookData> webhook() {
        return Mono.just(WebhookData.builder().chatId("123456789").from("admin").build());
    }
}
