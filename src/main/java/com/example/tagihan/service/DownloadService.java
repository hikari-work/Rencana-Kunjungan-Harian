package com.example.tagihan.service;

import com.example.tagihan.entity.Bills;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;


@Slf4j
@Service
public class DownloadService {

    private final WebClient webClient;

    public DownloadService() {
        this.webClient = WebClient.builder()
                .codecs(clientCodecConfigurer -> clientCodecConfigurer
                        .defaultCodecs()
                        .maxInMemorySize(50 * 1024 * 1024))
                .build();
    }

    public Flux<Bills> downloadAndParseCsv(String url) {
        return webClient.get()
                .uri(URI.create(url))
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .transform(dataBufferFlux ->
                        StringDecoder.textPlainOnly().decode(
                                dataBufferFlux,
                                ResolvableType.forClass(String.class),
                                null,
                                null)
                )
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split(","))
                .filter(columns -> columns.length >= 30)
                .map(columns -> {
                    for (int i = 0; i < columns.length; i++) {
                        columns[i] = removeQuotes(columns[i]);
                    }
                    return columns;
                })
                .map(this::mapToBill)
                .doOnError(e -> log.error("Streaming error: {}", e.getMessage()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String removeQuotes(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }


    public <T> Mono<T> downloadObject(String url, Class<T> responseType) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(responseType);
    }

    public <T> Mono<T> downloadWithTypeReference(String url, ParameterizedTypeReference<T> typeReference) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(typeReference);
    }

    public Mono<byte[]> downloadFile(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(byte[].class);
    }

    private Bills mapToBill(String[] line) {
        return Bills.builder()
                .customerId(line[0])
                .wilayah(line[1])
                .branch(line[2])
                .noSpk(line[3])
                .officeLocation(line[4])
                .product(line[5])
                .name(line[6])
                .address(line[7])
                .payDown(line[8])
                .realization(line[9])
                .dueDate(line[10])
                .collectStatus(line[11])
                .dayLate(line[12])
                .plafond(parseLong(line[13]))
                .debitTray(parseLong(line[14]))
                .interest(parseLong(line[15]))
                .principal(parseLong(line[16]))
                .installment(parseLong(line[17]))
                .lastInterest(parseLong(line[18]))
                .lastPrincipal(parseLong(line[19]))
                .lastInstallment(parseLong(line[20]))
                .fullPayment(parseLong(line[21]))
                .minInterest(parseLong(line[22]))
                .minPrincipal(parseLong(line[23]))
                .penaltyInterest(parseLong(line[24]))
                .penaltyPrincipal(parseLong(line[25]))
                .accountOfficer(line[26])
                .kios(line.length > 28 ? line[28] : "")
                .titipan(line.length > 29 ? parseLong(line[29]) : 0L)
                .fixedInterest(line.length > 30 ? parseLong(line[30]) : 0L)
                .build();
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}