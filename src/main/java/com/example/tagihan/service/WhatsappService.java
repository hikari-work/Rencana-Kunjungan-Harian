package com.example.tagihan.service;

import com.example.tagihan.dto.ResponseDTO;
import com.example.tagihan.dto.WhatsAppRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

@Slf4j
@Service
public class WhatsappService {


	private final WebClient webClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public WhatsappService(WebClient.Builder webClientBuilder,
						   @Value("${base.whatsapp.url}") String whatsappUrl,
						   @Value("${base.whatsapp.token}") String token,
						   @Value("${base.whatsapp.device.id}") String deviceId) {
		this.webClient = webClientBuilder
				.baseUrl(whatsappUrl)
				.defaultHeader("Authorization", "Basic " + Base64.getEncoder()
						.encodeToString((token).getBytes()))
				.defaultHeader("X-Device-Id", deviceId)
				.build();
	}
	public Mono<ResponseDTO> sendMessage(WhatsAppRequestDTO whatsappRequestDTO) {
		switch (whatsappRequestDTO.getType()) {
			case TEXT -> {
				return sendMessageText(whatsappRequestDTO);
			}
			case IMAGE -> {
				return sendImageMessage(whatsappRequestDTO);
			}
			case VIDEO -> {
				return sendVideoMessage(whatsappRequestDTO);
			}
			case DOCUMENT -> {
				return sendDocument(whatsappRequestDTO);
			}
			default -> {
				return Mono.empty();
			}
		}
	}

	public Mono<ResponseDTO> sendDocument(WhatsAppRequestDTO whatsappRequestDTO) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("phone", whatsappRequestDTO.getPhone());
		builder.part("is_forwarded", whatsappRequestDTO.isForwarded());
		builder.part("caption", whatsappRequestDTO.getCaption());
		if (whatsappRequestDTO.getMultipartFile() != null && !whatsappRequestDTO.getMultipartFile().isEmpty()) {
			try {
				builder.part("file", whatsappRequestDTO.getMultipartFile().getResource());
			} catch (Exception e) {
				return Mono.just(ResponseDTO.builder()
						.code("400")
						.message("Error")
						.build());
			}
		} else {
			return Mono.just(ResponseDTO.builder()
					.code("400")
					.message("Error")
					.build());
		}
		MultiValueMap<String, HttpEntity<?>> body = builder.build();
		return webClient
				.post()
				.uri("/send/file")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(body))
				.retrieve()
				.bodyToMono(ResponseDTO.class)
				.map(this::map)
				.onErrorResume(this::handleError);

	}

	public Mono<ResponseDTO> sendVideoMessage(WhatsAppRequestDTO requestDTO) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("phone", requestDTO.getPhone());
		builder.part("is_forwarded", requestDTO.isForwarded());
		if (requestDTO.getMultipartFile() != null && !requestDTO.getMultipartFile().isEmpty()) {
			try {
				builder.part("video", requestDTO.getMultipartFile().getResource());
			} catch (Exception e) {
				return Mono.just(ResponseDTO.builder()
								.code("400")
								.message("Error")
						.build());
			}
		}
		if (requestDTO.getVideoUrl() != null && !requestDTO.getVideoUrl().isEmpty()) {
			builder.part("video_url", requestDTO.getVideoUrl());
		}
		MultiValueMap<String, HttpEntity<?>> body = builder.build();
		return webClient
				.post()
				.uri("/send/video")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(body))
				.retrieve()
				.bodyToMono(ResponseDTO.class)
				.map(this::map)
				.onErrorResume(this::handleError);
	}
	private Mono<ResponseDTO> sendImageMessage(WhatsAppRequestDTO request) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("phone", request.getPhone());
		builder.part("caption", request.getCaption() != null ? request.getCaption() : "");
		builder.part("compress", request.getCompress());
		builder.part("is_forwarded", request.isForwarded());
		if (request.getMultipartFile() != null && !request.getMultipartFile().isEmpty()) {
			try {
				builder.part("image", request.getMultipartFile().getResource());
			} catch (Exception e) {
				return Mono.just(ResponseDTO.builder()
								.message("Error")
								.code("400")
						.build());
			}
		}
		if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
			builder.part("image_url", request.getImageUrl());
		}
		MultiValueMap<String, HttpEntity<?>> multipartData = builder.build();
		return webClient
				.post()
				.uri("/send/image")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(multipartData))
				.retrieve()
				.bodyToMono(ResponseDTO.class)
				.map(this::map)
				.onErrorResume(this::handleError);

	}
	public Mono<ResponseDTO> sendMessageText(WhatsAppRequestDTO whatsAppRequestDTO) {
		log.info("Sending Whatsapp");
		return webClient
				.post()
				.uri("/send/message")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(whatsAppRequestDTO)
				.exchangeToMono(response -> {
					if (response.statusCode().isError()) {
						return response.bodyToMono(String.class)
								.flatMap(errorBody -> {
									log.error("API Error Raw Response: {}", errorBody);
									return Mono.error(new RuntimeException("API Error: " + response.statusCode()));
								});
					}

					return response.bodyToMono(String.class)
							.doOnNext(rawBody -> log.info("Raw Response: {}", rawBody))
                            .<ResponseDTO>handle((rawBody, sink) -> {
                                try {
                                    sink.next(objectMapper.readValue(rawBody, ResponseDTO.class));
                                } catch (Exception e) {
                                    log.error("Mapping error: {}", e.getMessage());
                                    sink.error(new RuntimeException("Failed to parse response"));
                                }
                            });
				})
				.map(this::map)
				.doOnError(err -> log.error("Processing Error: {}", err.getMessage()))
				.onErrorResume(this::handleError);
	}


	private Mono<ResponseDTO> handleError(Throwable throwable) {
		return Mono.just(ResponseDTO.builder()
						.message("Error")
						.code("400")
				.build());
	}
	private ResponseDTO map(ResponseDTO responseDTO) {
		if (responseDTO.getCode().equals("200")) {
			return ResponseDTO.builder()
					.code(responseDTO.getCode())
					.message("OK")
					.build();
		} else {
			return ResponseDTO.builder()
					.code(responseDTO.getCode())
					.message("ERROR")
					.build();
		}
	}
}
