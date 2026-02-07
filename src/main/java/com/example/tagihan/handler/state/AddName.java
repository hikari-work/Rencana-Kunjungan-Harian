package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@StateHandler(state = State.ADD_NAME)
public class AddName implements StateHandlers {
    private final StateService stateService;

    public AddName(StateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public Mono<Void> handle(StateData stateData) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String jid = message.getPayload().getFrom();
        String text = message.getPayload().getBody();
        stateService.getUserState(jid).getVisit().setName(text);
        return stateService.setVisitData(jid, stateService.getUserState(jid).getVisit())
                .then();
    }
}
