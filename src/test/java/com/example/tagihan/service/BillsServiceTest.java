package com.example.tagihan.service;

import com.example.tagihan.entity.Bills;
import com.example.tagihan.repository.BillsRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillsServiceTest {

    @Mock
    private BillsRepo billsRepo;

    @Mock
    private DownloadService downloadService;

    @InjectMocks
    private BillsService billsService;

    @Captor
    private ArgumentCaptor<List<Bills>> batchCaptor;

    private String testUrl;

    @BeforeEach
    void setUp() {
        testUrl = "https://example.com/bills.csv";
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldDeleteAllBeforeSaving() {
        Bills bill1 = createBill("SPK001", "CUST001", 1000000L);
        Bills bill2 = createBill("SPK002", "CUST002", 2000000L);

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.just(bill1, bill2));
        when(billsRepo.saveAll(anyList())).thenReturn(Flux.just(bill1, bill2));

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(1)).deleteAll();
        verify(downloadService, times(1)).downloadAndParseCsv(testUrl);
        verify(billsRepo, times(1)).saveAll(anyList());
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldProcessInBatchesOf200() {
        List<Bills> bills = createBills(450);

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.fromIterable(bills));
        when(billsRepo.saveAll(anyList())).thenReturn(Flux.empty());

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(3)).saveAll(batchCaptor.capture());

        List<List<Bills>> batches = batchCaptor.getAllValues();
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(200);
        assertThat(batches.get(1)).hasSize(200);
        assertThat(batches.get(2)).hasSize(50);
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldHandleSingleBatch() {
        List<Bills> bills = createBills(150);

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.fromIterable(bills));
        when(billsRepo.saveAll(anyList())).thenReturn(Flux.empty());

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(1)).saveAll(batchCaptor.capture());

        List<Bills> batch = batchCaptor.getValue();
        assertThat(batch).hasSize(150);
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldHandleExactly200Bills() {
        List<Bills> bills = createBills(200);

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.fromIterable(bills));
        when(billsRepo.saveAll(anyList())).thenReturn(Flux.empty());

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(1)).saveAll(batchCaptor.capture());

        List<Bills> batch = batchCaptor.getValue();
        assertThat(batch).hasSize(200);
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldHandleEmptyData() {
        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.empty());

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(1)).deleteAll();
        verify(downloadService, times(1)).downloadAndParseCsv(testUrl);
        verify(billsRepo, never()).saveAll(anyList());
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldHandleDeleteError() {
        RuntimeException deleteError = new RuntimeException("Delete failed");
        when(billsRepo.deleteAll()).thenReturn(Mono.error(deleteError));

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(billsRepo, times(1)).deleteAll();
        verify(billsRepo, never()).saveAll(anyList());
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldHandleDownloadError() {
        RuntimeException downloadError = new RuntimeException("Download failed");
        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.error(downloadError));

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(billsRepo, times(1)).deleteAll();
        verify(downloadService, times(1)).downloadAndParseCsv(testUrl);
        verify(billsRepo, never()).saveAll(anyList());
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldHandleSaveError() {
        List<Bills> bills = createBills(50);
        RuntimeException saveError = new RuntimeException("Save failed");

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.fromIterable(bills));
        when(billsRepo.saveAll(anyList())).thenReturn(Flux.error(saveError));

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(billsRepo, times(1)).deleteAll();
        verify(downloadService, times(1)).downloadAndParseCsv(testUrl);
        verify(billsRepo, times(1)).saveAll(anyList());
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldHandlePartialBatchFailure() {
        List<Bills> bills = createBills(250);
        RuntimeException saveError = new RuntimeException("Batch 2 save failed");

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.fromIterable(bills));

        when(billsRepo.saveAll(anyList()))
                .thenReturn(Flux.empty())
                .thenReturn(Flux.error(saveError));

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(billsRepo, times(1)).deleteAll();
        verify(billsRepo, times(2)).saveAll(anyList());
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldProcessMultipleBatchesSequentially() {
        List<Bills> bills = createBills(600);

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.fromIterable(bills));
        when(billsRepo.saveAll(anyList())).thenReturn(Flux.empty());

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(3)).saveAll(batchCaptor.capture());

        List<List<Bills>> batches = batchCaptor.getAllValues();
        assertThat(batches).hasSize(3);
        assertThat(batches.get(0)).hasSize(200);
        assertThat(batches.get(1)).hasSize(200);
        assertThat(batches.get(2)).hasSize(200);
    }

    @Test
    void findBillBySpk_ShouldReturnBillWhenExists() {
        String spk = "SPK001";
        Bills expectedBill = createBill(spk, "CUST001", 1000000L);

        when(billsRepo.findById(spk)).thenReturn(Mono.just(expectedBill));

        Mono<Bills> result = billsService.findBillBySpk(spk);

        StepVerifier.create(result)
                .assertNext(bill -> {
                    assertThat(bill.getNoSpk()).isEqualTo(spk);
                    assertThat(bill.getCustomerId()).isEqualTo("CUST001");
                    assertThat(bill.getPlafond()).isEqualTo(1000000L);
                })
                .verifyComplete();

        verify(billsRepo, times(1)).findById(spk);
    }

    @Test
    void findBillBySpk_ShouldReturnEmptyWhenNotExists() {
        String spk = "SPK999";

        when(billsRepo.findById(spk)).thenReturn(Mono.empty());

        Mono<Bills> result = billsService.findBillBySpk(spk);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(1)).findById(spk);
    }

    @Test
    void findBillBySpk_ShouldHandleRepositoryError() {
        String spk = "SPK001";
        RuntimeException repositoryError = new RuntimeException("Database connection failed");

        when(billsRepo.findById(spk)).thenReturn(Mono.error(repositoryError));

        Mono<Bills> result = billsService.findBillBySpk(spk);

        StepVerifier.create(result)
                .expectError(RuntimeException.class)
                .verify();

        verify(billsRepo, times(1)).findById(spk);
    }

    @Test
    void findBillBySpk_ShouldHandleNullSpk() {
        String spk = null;

        when(billsRepo.findById(spk)).thenReturn(Mono.empty());

        Mono<Bills> result = billsService.findBillBySpk(spk);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(1)).findById(spk);
    }

    @Test
    void findBillBySpk_ShouldHandleEmptySpk() {
        String spk = "";

        when(billsRepo.findById(spk)).thenReturn(Mono.empty());

        Mono<Bills> result = billsService.findBillBySpk(spk);

        StepVerifier.create(result)
                .verifyComplete();

        verify(billsRepo, times(1)).findById(spk);
    }

    @Test
    void saveAndDeleteBillsReactive_ShouldVerifyOrderOfOperations() {
        Bills bill = createBill("SPK001", "CUST001", 1000000L);

        when(billsRepo.deleteAll()).thenReturn(Mono.empty());
        when(downloadService.downloadAndParseCsv(testUrl))
                .thenReturn(Flux.just(bill));
        when(billsRepo.saveAll(anyList())).thenReturn(Flux.just(bill));

        Mono<Void> result = billsService.saveAndDeleteBillsReactive(testUrl);

        StepVerifier.create(result)
                .verifyComplete();

        var inOrder = inOrder(billsRepo, downloadService);
        inOrder.verify(billsRepo).deleteAll();
        inOrder.verify(downloadService).downloadAndParseCsv(testUrl);
        inOrder.verify(billsRepo).saveAll(anyList());
    }

    private Bills createBill(String spk, String customerId, Long plafond) {
        return Bills.builder()
                .noSpk(spk)
                .customerId(customerId)
                .wilayah("Jakarta")
                .branch("Cabang Jakarta")
                .officeLocation("Gedung A Lt 5")
                .product("Kredit Multiguna")
                .name("Customer Name")
                .address("Jl. Test No. 123")
                .payDown("1000000")
                .realization("5000000")
                .dueDate("2024-01-15")
                .collectStatus("Lancar")
                .dayLate("0")
                .plafond(plafond)
                .debitTray(500000L)
                .interest(100000L)
                .principal(200000L)
                .installment(300000L)
                .lastInterest(95000L)
                .lastPrincipal(195000L)
                .lastInstallment(290000L)
                .fullPayment(plafond + 100000L)
                .minInterest(90000L)
                .minPrincipal(190000L)
                .penaltyInterest(10000L)
                .penaltyPrincipal(5000L)
                .accountOfficer("AO-001")
                .kios("Kios A")
                .titipan(50000L)
                .fixedInterest(25000L)
                .build();
    }

    private List<Bills> createBills(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> createBill(
                        "SPK" + String.format("%03d", i),
                        "CUST" + String.format("%03d", i),
                        1000000L + (i * 10000L)
                ))
                .toList();
    }
}