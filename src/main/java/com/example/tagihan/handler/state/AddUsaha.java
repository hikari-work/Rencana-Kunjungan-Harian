package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@StateHandler(state = State.ADD_USAHA)
@Slf4j
@RequiredArgsConstructor
public class AddUsaha implements StateHandlers {
    private final StateService stateService;

    @Override
    public Mono<Void> handle(StateData stateData) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return Mono.defer(() -> {
            String text = message.getPayload().getBody();
            String jid = message.getPayload().getFrom();
           if (text.isBlank()) {
               return Mono.empty();
           }
            StateData userState = stateService.getUserState(jid);
            if (userState == null) {
                return Mono.empty();
            }
            userState.getVisit().setUsaha(text);
            return stateService.setVisitData(jid, userState.getVisit())
                    .doOnSuccess(v -> log.info("Usaha added successfully for {}", jid))
                    .then();
        });
    }
}
