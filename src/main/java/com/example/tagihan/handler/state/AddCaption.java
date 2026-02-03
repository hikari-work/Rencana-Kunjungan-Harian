package com.example.tagihan.handler.state;


import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@StateHandler(state = State.ADD_CAPTION)
@Component
public class AddCaption implements StateHandlers {
    private final StateService stateService;

    public AddCaption(StateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public Mono<Void> handle(StateData stateData) {
        log.info("Adding caption to visit {}", stateData.getVisit());
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return Mono.defer(() -> {
            try {
                if (message == null || message.getPayload() == null) {
                    log.error("Invalid payload received");
                    return Mono.empty();
                }

                String chatId = message.getPayload().getFrom();
                String caption = message.getPayload().getBody();

                if (chatId == null || chatId.isBlank()) {
                    log.error("ChatId is null or blank");
                    return Mono.empty();
                }

                if (caption == null || caption.isBlank()) {
                    log.warn("Caption is null or blank for chatId: {}", chatId);
                    return Mono.empty();
                }

                log.info("Processing ADD_CAPTION for chatId: {} with caption: {}", chatId, caption);

                StateData userState = stateService.getUserState(chatId);

                if (userState == null) {
                    log.error("No user state found for chatId: {}", chatId);
                    return Mono.empty();
                }

                Visit visit = userState.getVisit();
                if (visit == null) {
                    log.error("No visit data found in state for chatId: {}", chatId);
                    return Mono.empty();
                }

                visit.setNote(caption);
                log.info("Setting caption '{}' for visit: {}", caption, visit.getName());

                return stateService.setVisitData(chatId, visit)
                        .doOnSubscribe(sub -> log.info("Saving visit data for chatId: {}", chatId))
                        .doOnSuccess(savedState -> log.info("Visit data saved successfully for chatId: {}", chatId))
                        .flatMap(savedState -> {
                            log.info("Dispatching state after ADD_CAPTION for chatId: {}", chatId);
                            return Mono.empty();
                        })
                        .doOnSuccess(v -> log.info("State dispatched successfully for chatId: {}", chatId))
                        .then()
                        .doOnError(e -> log.error("Error in reactive chain for chatId: {}", chatId, e))
                        .onErrorResume(e -> {
                            log.error("Recovered from error in ADD_CAPTION handler for chatId: {}", chatId, e);
                            return Mono.empty();
                        });

            } catch (Exception e) {
                log.error("Exception in ADD_CAPTION handler", e);
                return Mono.empty();
            }
        });
    }
}