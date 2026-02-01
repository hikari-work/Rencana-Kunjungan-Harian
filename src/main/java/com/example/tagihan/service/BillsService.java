package com.example.tagihan.service;

import com.example.tagihan.entity.Bills;
import com.example.tagihan.repository.BillsRepo;
import com.mongodb.MongoTimeoutException;
import io.netty.handler.timeout.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;


@Slf4j
@Service
@RequiredArgsConstructor
public class BillsService {

    private final BillsRepo billsRepo;
    private final DownloadService downloadService;

    public Mono<Long> saveAndDeleteBillsReactive(String url) {
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        return billsRepo.deleteAll()
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(10))
                        .filter(throwable ->
                                throwable instanceof MongoTimeoutException ||
                                        throwable instanceof TimeoutException
                        )
                        .doBeforeRetry(signal ->
                                log.warn("Retry delete operation, attempt: {}", signal.totalRetries() + 1)
                        )
                )
                .doOnSuccess(v -> log.info("Semua data lama dihapus."))
                .onErrorResume(error -> {
                    log.error("Gagal menghapus data lama: {}", error.getMessage());
                    return Mono.empty();
                })
                .thenMany(downloadService.downloadAndParseCsv(url))
                .buffer(200)
                .concatMap(batch -> {
                    log.info("Memproses batch sejumlah {} ke MongoDB...", batch.size());

                    return billsRepo.saveAll(batch)
                            .timeout(Duration.ofSeconds(30))
                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                                    .maxBackoff(Duration.ofSeconds(10))
                                    .filter(throwable ->
                                            throwable instanceof MongoTimeoutException ||
                                                    throwable instanceof TimeoutException
                                    )
                                    .doBeforeRetry(signal ->
                                            log.warn("Retry batch save, attempt: {}, batch size: {}",
                                                    signal.totalRetries() + 1, batch.size())
                                    )
                            )
                            .doOnNext(saved -> successCount.incrementAndGet())
                            .onErrorResume(error -> {
                                log.warn("Batch insert gagal ({}), mencoba satu per satu", error.getMessage());

                                return Flux.fromIterable(batch)
                                        .flatMap(bill ->
                                                        billsRepo.save(bill)
                                                                .timeout(Duration.ofSeconds(10))
                                                                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                                                                        .maxBackoff(Duration.ofSeconds(5))
                                                                        .filter(throwable ->
                                                                                throwable instanceof MongoTimeoutException ||
                                                                                        throwable instanceof TimeoutException
                                                                        )
                                                                )
                                                                .doOnSuccess(saved -> successCount.incrementAndGet())
                                                                .onErrorResume(individualError -> {
                                                                    errorCount.incrementAndGet();
                                                                    log.warn("Gagal menyimpan data setelah retry: {}",
                                                                            individualError.getMessage());
                                                                    return Mono.empty();
                                                                })
                                                , 10);
                            });
                })
                .count()
                .timeout(Duration.ofMinutes(10))
                .doOnSuccess(total -> {
                    log.info("Proses sinkronisasi selesai!");
                    log.info("Berhasil disimpan: {}", successCount.get());
                    if (errorCount.get() > 0) {
                        log.warn("Gagal disimpan: {}", errorCount.get());
                    }
                })
                .doOnError(e -> log.error("Terjadi kegagalan: {}", e.getMessage()))
                .map(total -> successCount.get());
    }
    public Mono<Bills> findBillBySpk(String spk) {
        return billsRepo.findByNoSpk((spk));
    }
}