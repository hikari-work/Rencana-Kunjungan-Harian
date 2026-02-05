package com.example.tagihan.handler.state;

import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.util.DateRangeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.DateTimeException;
import java.time.LocalDate;

@Slf4j
@StateHandler(state = State.ADD_CAPTION)
@Component
@RequiredArgsConstructor
public class AddCaption implements StateHandlers {
    private final StateService stateService;

    @Override
    public Mono<Void> handle(StateData stateData) {
        log.info("Adding caption to visit {}", stateData.getVisit());
        return Mono.empty();
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        return Mono.defer(() -> {
            if (!isValidPayload(message)) {
                return Mono.empty();
            }

            String chatId = message.getPayload().getFrom();
            String caption = message.getPayload().getBody();

            if (!isValidChatId(chatId)) {
                return Mono.empty();
            }

            if (!isValidCaption(caption, chatId)) {
                return Mono.empty();
            }

            log.info("Processing ADD_CAPTION for chatId: {} with caption: {}", chatId, caption);

            return processCaption(chatId, caption);
        });
    }

    private boolean isValidPayload(WebhookPayload message) {
        if (message == null || message.getPayload() == null) {
            log.error("Invalid payload received");
            return false;
        }
        return true;
    }

    private boolean isValidChatId(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            log.error("ChatId is null or blank");
            return false;
        }
        return true;
    }

    private boolean isValidCaption(String caption, String chatId) {
        if (caption == null || caption.isBlank()) {
            log.warn("Caption is null or blank for chatId: {}", chatId);
            return false;
        }
        return true;
    }

    private Mono<Void> processCaption(String chatId, String caption) {
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

        updateVisitWithCaption(visit, caption);

        return saveVisitData(chatId, visit);
    }

    private void updateVisitWithCaption(Visit visit, String caption) {
        String finalCaption = caption.equalsIgnoreCase("kosong") ? "" : caption;
        visit.setNote(finalCaption);

        try {
            LocalDate dateReminder = DateRangeUtil.parseDate(caption);
            visit.setReminderDate(dateReminder);
            log.info("Reminder date set to: {}", dateReminder);
        } catch (DateTimeException e) {
            log.debug("Caption does not contain a valid date format: {}", e.getMessage());
        }

        log.info("Setting caption '{}' for visit: {}", finalCaption, visit.getName());
    }

    private Mono<Void> saveVisitData(String chatId, Visit visit) {
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
    }
}