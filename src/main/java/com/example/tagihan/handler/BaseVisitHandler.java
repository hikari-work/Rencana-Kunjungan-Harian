package com.example.tagihan.handler;

import com.example.tagihan.dispatcher.Messagehandler;
import com.example.tagihan.dispatcher.StateDispatcher;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.entity.Bills;
import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.exception.BillNotFoundException;
import com.example.tagihan.service.BillsService;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import com.example.tagihan.util.CurrencyUtil;
import com.example.tagihan.util.DateRangeUtil;
import com.example.tagihan.util.NumberParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;


@Slf4j
@Component
@RequiredArgsConstructor
public abstract class BaseVisitHandler implements Messagehandler {

    private static final String BILL_NOT_FOUND_MESSAGE = "No SPK tidak ditemukan";
    private static final String GENERAL_ERROR_MESSAGE = "Terjadi kesalahan saat memproses tagihan";
    private static final String MISSING_SPK_MESSAGE = "Anda belum memasukkan no SPK";
    private static final String MISSING_NOTE_MESSAGE = "Anda belum memasukkan catatan";
    private static final String ONGOING_PROCESS_MESSAGE_TEMPLATE =
            "Anda memiliki proses pengisian LKN/RKH yang belum selesai: %s. Harap selesaikan terlebih dahulu.";
    private static final long MINIMUM_APPOINTMENT_VALUE = 3000;
    private static final String GROUP_CHAT_SUFFIX = "@g.us";

    private final StateService stateService;
    private final WhatsappService whatsappService;
    private final BillsService billsService;
    private final StateDispatcher stateDispatcher;

    protected abstract VisitType getVisitType();


    protected abstract String getCommandPrefix();

    protected boolean requiresSpk() {
        return getVisitType() != VisitType.CANVASING &&
                getVisitType() != VisitType.SURVEY;
    }

    @Override
    public Mono<Void> handle(WebhookPayload message) {
        try {
            MessageContext context = extractMessageContext(message);

            if (stateService.isUserInState(context.chatId())) {
                return handleOngoingProcess(context);
            }

            return validateAndProcessCommand(message, context);
        } catch (Exception e) {
            log.error("Error handling message: {}", message, e);
            return sendErrorMessage(message, GENERAL_ERROR_MESSAGE);
        }
    }

    private MessageContext extractMessageContext(WebhookPayload message) {
        String chatId = message.getPayload().getFrom();
        String groupId = message.getPayload().getChatId();
        String rawText = message.getPayload().getBody();
        String text = rawText.substring(getCommandPrefix().length()).trim();

        return new MessageContext(chatId, groupId, text);
    }

    private Mono<Void> validateAndProcessCommand(WebhookPayload message, MessageContext context) {
        CommandInput input = parseCommandInput(context.text());

        if (requiresSpk()) {
            if (!input.isValid()) {
                return sendErrorMessage(message, MISSING_SPK_MESSAGE);
            }
            return processBillWithSpk(message, context, input);
        } else {
            if (context.text().trim().isEmpty()) {
                return sendErrorMessage(message, MISSING_NOTE_MESSAGE);
            }
            return processCanvasing(context, context.text().trim());
        }
    }

    private CommandInput parseCommandInput(String text) {
        String[] parts = text.split("\\s+", 2);
        String spk = parts.length > 0 ? parts[0].trim() : "";
        String additionalParams = parts.length > 1 ? parts[1].trim() : null;

        return new CommandInput(spk, additionalParams);
    }

    private Mono<Void> processBillWithSpk(WebhookPayload message, MessageContext context, CommandInput input) {
        return billsService.findBillBySpk(input.spk())
                .doOnSubscribe(sub -> log.info("Searching bill with SPK: '{}'", input.spk()))
                .doOnNext(bill -> log.info("Bill found for SPK: {}", input.spk()))
                .switchIfEmpty(handleBillNotFound(message, input.spk()))
                .flatMap(bill -> processFoundBill(context, bill, input.additionalParams()))
                .onErrorResume(BillNotFoundException.class, e -> Mono.empty())
                .onErrorResume(e -> handleUnexpectedError(message, input.spk(), e));
    }

    private Mono<Void> processCanvasing(MessageContext context, String note) {
        log.info("Processing canvasing for chatId: {} with note: {}", context.chatId(), note);

        VisitParameters params = parseVisitParameters(note);
        Visit visit = buildCanvasingVisit(context.chatId(), params);

        return stateService.setVisitData(context.chatId(), visit)
                .doOnNext(state -> log.info("Canvasing visit data saved for chatId: {}, next state: {}",
                        context.chatId(), state))
                .flatMap(state -> dispatchState(context.chatId()))
                .doOnSuccess(v -> log.info("Canvasing processing completed successfully for chatId: {}",
                        context.chatId()))
                .doOnError(e -> log.error("Error processing canvasing for chatId: {}",
                        context.chatId(), e))
                .then();
    }

    private Mono<Bills> handleBillNotFound(WebhookPayload message, String spk) {
        return Mono.defer(() -> {
            log.warn("Bill not found for SPK: '{}'", spk);
            return sendErrorMessage(message, BILL_NOT_FOUND_MESSAGE)
                    .then(Mono.error(new BillNotFoundException(spk)));
        });
    }

    private Mono<Void> processFoundBill(MessageContext context, Bills bill, String additionalParams) {
        VisitParameters params = parseVisitParameters(additionalParams);

        log.info("Processing bill - SPK: {}, reminder: {}, appointment: {}",
                bill.getNoSpk(), params.reminder(), params.appointment());

        return createAndSendVisit(context, bill, params);
    }

    private VisitParameters parseVisitParameters(String additionalParams) {
        if (additionalParams == null) {
            return new VisitParameters(null, null, null);
        }

        LocalDate reminder = DateRangeUtil.parseReminder(additionalParams);
        Long appointment = parseAndValidateAppointment(additionalParams);

        return new VisitParameters(additionalParams, reminder, appointment);
    }

    private Long parseAndValidateAppointment(String params) {
        Long appointment = NumberParser.parseFirstNumber(params);

        if (appointment != null && appointment < MINIMUM_APPOINTMENT_VALUE) {
            log.debug("Appointment value {} below minimum threshold, setting to null", appointment);
            return null;
        }

        return appointment;
    }

    private Mono<Void> createAndSendVisit(MessageContext context, Bills bill, VisitParameters params) {
        Visit visit = buildVisit(context.chatId(), bill, params);

        return stateService.setVisitData(context.chatId(), visit)
                .doOnNext(state -> log.info("Visit data saved for chatId: {}, next state: {}",
                        context.chatId(), state))
                .flatMap(state -> sendNotificationIfNeeded(context, bill, stateService.getUserState(context.chatId())))
                .flatMap(state -> dispatchState(context.chatId()))
                .doOnSuccess(v -> log.info("Bill processing completed successfully for chatId: {}",
                        context.chatId()))
                .doOnError(e -> log.error("Error processing bill for chatId: {}",
                        context.chatId(), e))
                .then();
    }

    private Mono<StateData> sendNotificationIfNeeded(MessageContext context, Bills bill, StateData state) {
        if (isGroupChat(context.groupId())) {
            return sendGroupNotification(context.groupId(), bill)
                    .then(Mono.just(state));
        }
        return Mono.just(state);
    }

    private Mono<Void> dispatchState(String chatId) {
        return stateDispatcher.dispatch(stateService.getUserState(chatId));
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
                .doOnError(e -> log.error("Failed to send group notification to {}", chatId, e))
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

    private Visit buildVisit(String chatId, Bills bill, VisitParameters params) {
        return Visit.builder()
                .spk(bill.getNoSpk())
                .name(bill.getName())
                .note(params.note())
                .address(bill.getAddress())
                .appointment(params.appointment())
                .reminderDate(params.reminder())
                .userId(chatId)
                .debitTray(bill.getDebitTray())
                .penalty(bill.getPenaltyInterest() + bill.getPenaltyPrincipal())
                .interest(bill.getLastInterest())
                .principal(bill.getLastPrincipal())
                .plafond(bill.getPlafond())
                .visitType(getVisitType())
                .visitDate(Instant.now())
                .imageUrl(null)
                .build();
    }

    /**
     * Build Visit untuk canvasing tanpa SPK
     */
    private Visit buildCanvasingVisit(String chatId, VisitParameters params) {
        return Visit.builder()
                .spk(null)  // Tidak ada SPK untuk canvasing
                .name(null)
                .note(params.note())
                .address(null)
                .appointment(params.appointment())
                .reminderDate(params.reminder())
                .userId(chatId)
                .debitTray(null)
                .penalty(null)
                .interest(null)
                .principal(null)
                .plafond(null)
                .visitType(getVisitType())
                .visitDate(Instant.now())
                .imageUrl(null)
                .build();
    }

    private Mono<Void> handleOngoingProcess(MessageContext context) {
        StateData userState = stateService.getUserState(context.chatId());
        String identifier = Optional.ofNullable(userState.getVisit())
                .map(visit -> Optional.ofNullable(visit.getSpk()).orElse(visit.getName()))
                .orElse("N/A");

        String message = String.format(ONGOING_PROCESS_MESSAGE_TEMPLATE, identifier);

        WhatsAppRequestDTO reminderRequest = WhatsAppRequestDTO.builder()
                .phone(context.chatId())
                .message(message)
                .build();

        return whatsappService.sendMessageText(reminderRequest)
                .doOnSubscribe(sub -> log.info("Sending ongoing process reminder to {}", context.chatId()))
                .then();
    }

    private Mono<Void> sendErrorMessage(WebhookPayload message, String errorMessage) {
        WhatsAppRequestDTO errorRequest = WhatsAppRequestDTO.builder()
                .phone(message.getPayload().getChatId())
                .replyToMessageId(message.getPayload().getId())
                .message(errorMessage)
                .build();

        return whatsappService.sendMessageText(errorRequest)
                .doOnSubscribe(sub -> log.info("Sending error message: {}", errorMessage))
                .doOnError(e -> log.error("Failed to send error message", e))
                .then();
    }

    private Mono<Void> handleUnexpectedError(WebhookPayload message, String spk, Throwable e) {
        log.error("Unexpected error processing bill for SPK: {}", spk, e);
        return sendErrorMessage(message, GENERAL_ERROR_MESSAGE)
                .then(Mono.empty());
    }

    private boolean isGroupChat(String chatId) {
        return chatId != null && chatId.contains(GROUP_CHAT_SUFFIX);
    }

    private record MessageContext(String chatId, String groupId, String text) { }

    private record CommandInput(String spk, String additionalParams) {
        public boolean isValid() {
            return spk != null && !spk.isEmpty();
        }
    }

    private record VisitParameters(String note, LocalDate reminder, Long appointment) { }
}