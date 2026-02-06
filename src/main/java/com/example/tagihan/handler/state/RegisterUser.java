package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@StateHandler(state = State.REGISTER)
@Slf4j
public class RegisterUser implements StateHandlers {
    private final UserService userService;
    private final StateService stateService;

    public RegisterUser(UserService userService, StateService stateService) {
        this.userService = userService;
        this.stateService = stateService;
    }

    @Override
    public Mono<Void> handle(StateData stateData) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        String jid = message.getPayload().getFrom();
        String accountOfficer = message.getPayload().getBody().toUpperCase();

        log.info("Registering user {}", jid);

        return userService.saveUser(jid, accountOfficer)
                .flatMap(user -> {
                    StateData userState = stateService.getUserState(jid);
                    userState.getVisit().setUserId(jid);
                    return stateService.setVisitData(jid, userState.getVisit());
                })
                .then();
    }
}