package com.example.tagihan.service;

import com.example.tagihan.entity.Bills;
import com.example.tagihan.repository.BillsRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillsService {

    private final BillsRepo billsRepo;
    private final DownloadService downloadService;

    public Mono<Void> saveAndDeleteBillsReactive(String url) {
        return billsRepo.deleteAll()
                .doOnSuccess(v -> log.info("Semua data lama dihapus."))
                .thenMany(downloadService.downloadAndParseCsv(url))
                .buffer(200)
                .flatMap(batch -> {
                    log.info("Mengirim batch sejumlah {} ke MongoDB...", batch.size());
                    return billsRepo.saveAll(batch);
                })
                .then()
                .doOnSuccess(v -> log.info("Proses sinkronisasi selesai!"))
                .doOnError(e -> log.error("Terjadi kegagalan: {}", e.getMessage()));
    }
    private Mono<List<Bills>> downloadAndSave(String url) {
        return downloadService.downloadWithTypeReference(
                url,
                new ParameterizedTypeReference<>() {
                }
        );
    }
}