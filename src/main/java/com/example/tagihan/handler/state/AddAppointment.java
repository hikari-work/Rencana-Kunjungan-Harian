package com.example.tagihan.handler.state;


import com.example.tagihan.dispatcher.StateHandler;
import com.example.tagihan.dispatcher.StateHandlers;
import com.example.tagihan.dto.WebhookPayload;
import com.example.tagihan.dto.WhatsAppMessageType;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import com.example.tagihan.service.State;
import com.example.tagihan.service.StateData;
import com.example.tagihan.service.StateService;
import com.example.tagihan.service.WhatsappService;
import com.example.tagihan.util.NumberParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Transient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@StateHandler(state = State.ADD_APPOINTMENT)
@Component
@RequiredArgsConstructor
public class AddAppointment implements StateHandlers {
	@Transient
	private final WhatsappService whatsappService;
	private final StateService stateService;

	@Override
	public Mono<Void> handle(StateData stateData) {
		return Mono.justOrEmpty(null);
	}

	@Override
	public Mono<Void> handle(WebhookPayload message) {
		String text = message.getPayload().getBody();
		Long appointment = NumberParser.parseFirstNumber(text);
		if (appointment == null) {
			WhatsAppRequestDTO requestDTO = WhatsAppRequestDTO.builder()
					.type(WhatsAppMessageType.TEXT)
					.message("Saya tidak dapat menemukan nominalnya")
					.phone(message.getPayload().getFrom())
					.build();
			return whatsappService.sendMessage(requestDTO)
					.then();
		}
		String chatId = message.getPayload().getFrom();
		StateData data = stateService.getUserState(chatId);
		data.getVisit().setAppointment(appointment);
		return stateService.setVisitData(chatId, data.getVisit())
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
					log.error("Recovered from error in ADD_Appointment handler for chatId: {}", chatId, e);
					return Mono.empty();
				});
	}
}
