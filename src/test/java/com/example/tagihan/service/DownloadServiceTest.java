package com.example.tagihan.service;

import com.example.tagihan.entity.Bills;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class DownloadServiceTest {

    private DownloadService downloadService;
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        downloadService = new DownloadService();
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void shouldMapCsvRowToBillsObject_WithAllFields() {
        String csvData = buildCsvHeader() + "\n" +
                "CUST001,Jakarta Pusat,Cabang Jakarta,SPK-2024-001,Gedung A Lt 5,Kredit Multiguna," +
                "Budi Santoso,Jl. Sudirman No. 123 Jakarta,5000000,10000000," +
                "2024-01-15,Lancar,0,50000000,2500000,500000,1000000,1500000," +
                "450000,950000,1400000,52500000,400000,900000,50000,25000," +
                "AO-Jakarta-001,dummy,Kios A,1000000,250000";

        mockWebServer.enqueue(new MockResponse()
                .setBody(csvData)
                .setHeader("Content-Type", "text/csv"));

        String url = mockWebServer.url("/bills.csv").toString();

        var result = downloadService.downloadAndParseCsv(url);

        StepVerifier.create(result)
                .assertNext(bill -> {
                    assertThat(bill.getCustomerId()).isEqualTo("CUST001");
                    assertThat(bill.getWilayah()).isEqualTo("Jakarta Pusat");
                    assertThat(bill.getBranch()).isEqualTo("Cabang Jakarta");
                    assertThat(bill.getNoSpk()).isEqualTo("SPK-2024-001");
                    assertThat(bill.getOfficeLocation()).isEqualTo("Gedung A Lt 5");
                    assertThat(bill.getProduct()).isEqualTo("Kredit Multiguna");

                    assertThat(bill.getName()).isEqualTo("Budi Santoso");
                    assertThat(bill.getAddress()).isEqualTo("Jl. Sudirman No. 123 Jakarta");

                    assertThat(bill.getPayDown()).isEqualTo("5000000");
                    assertThat(bill.getRealization()).isEqualTo("10000000");
                    assertThat(bill.getDueDate()).isEqualTo("2024-01-15");
                    assertThat(bill.getCollectStatus()).isEqualTo("Lancar");
                    assertThat(bill.getDayLate()).isEqualTo("0");

                    assertThat(bill.getPlafond()).isEqualTo(50000000L);
                    assertThat(bill.getDebitTray()).isEqualTo(2500000L);
                    assertThat(bill.getInterest()).isEqualTo(500000L);
                    assertThat(bill.getPrincipal()).isEqualTo(1000000L);
                    assertThat(bill.getInstallment()).isEqualTo(1500000L);
                    assertThat(bill.getLastInterest()).isEqualTo(450000L);
                    assertThat(bill.getLastPrincipal()).isEqualTo(950000L);
                    assertThat(bill.getLastInstallment()).isEqualTo(1400000L);
                    assertThat(bill.getFullPayment()).isEqualTo(52500000L);
                    assertThat(bill.getMinInterest()).isEqualTo(400000L);
                    assertThat(bill.getMinPrincipal()).isEqualTo(900000L);
                    assertThat(bill.getPenaltyInterest()).isEqualTo(50000L);
                    assertThat(bill.getPenaltyPrincipal()).isEqualTo(25000L);

                    assertThat(bill.getAccountOfficer()).isEqualTo("AO-Jakarta-001");

                    assertThat(bill.getKios()).isEqualTo("Kios A");
                    assertThat(bill.getTitipan()).isEqualTo(1000000L);
                    assertThat(bill.getFixedInterest()).isEqualTo(250000L);
                })
                .verifyComplete();
    }

    @Test
    void shouldMapCsvRowToBillsObject_WithNumericEdgeCases() {
        String csvData = buildCsvHeader() + "\n" +
                "CUST003,Surabaya,Cabang Surabaya,SPK-2024-003,Gedung C Lt 2,Kredit Modal," +
                "Ahmad Yani,Jl. Pemuda No. 78 Surabaya,2000000,6000000," +
                "2024-03-10,Macet,45,0,0,0,0,0," +
                "0,0,0,0,0,0,0,0," +
                "AO-Surabaya-003,dummy,Kios B,0,0";

        mockWebServer.enqueue(new MockResponse()
                .setBody(csvData)
                .setHeader("Content-Type", "text/csv"));

        String url = mockWebServer.url("/bills.csv").toString();

        var result = downloadService.downloadAndParseCsv(url);

        StepVerifier.create(result)
                .assertNext(bill -> {
                    assertThat(bill.getCustomerId()).isEqualTo("CUST003");
                    assertThat(bill.getPlafond()).isEqualTo(0L);
                    assertThat(bill.getDebitTray()).isEqualTo(0L);
                    assertThat(bill.getInterest()).isEqualTo(0L);
                    assertThat(bill.getPrincipal()).isEqualTo(0L);
                    assertThat(bill.getInstallment()).isEqualTo(0L);
                    assertThat(bill.getTitipan()).isEqualTo(0L);
                    assertThat(bill.getFixedInterest()).isEqualTo(0L);
                })
                .verifyComplete();
    }

    @Test
    void shouldMapCsvRowToBillsObject_WithInvalidNumericValues() {
        String csvData = buildCsvHeader() + "\n" +
                "CUST004,Medan,Cabang Medan,SPK-2024-004,Gedung D Lt 1,Kredit Usaha," +
                "Dewi Sartika,Jl. Gatot Subroto No. 90 Medan,1500000,5000000," +
                "2024-04-05,Lancar,0,INVALID,2000000,invalid,800000,1200000," +
                "N/A,750000,1150000,40000000,350000,NULL,40000,20000," +
                "AO-Medan-004,dummy,Kios C,INVALID,invalid";

        mockWebServer.enqueue(new MockResponse()
                .setBody(csvData)
                .setHeader("Content-Type", "text/csv"));

        String url = mockWebServer.url("/bills.csv").toString();

        var result = downloadService.downloadAndParseCsv(url);

        StepVerifier.create(result)
                .assertNext(bill -> {
                    assertThat(bill.getCustomerId()).isEqualTo("CUST004");
                    assertThat(bill.getPlafond()).isEqualTo(0L); // INVALID
                    assertThat(bill.getDebitTray()).isEqualTo(2000000L); // valid
                    assertThat(bill.getInterest()).isEqualTo(0L); // invalid
                    assertThat(bill.getPrincipal()).isEqualTo(800000L); // valid
                    assertThat(bill.getLastInterest()).isEqualTo(0L); // N/A
                    assertThat(bill.getMinPrincipal()).isEqualTo(0L); // NULL
                    assertThat(bill.getTitipan()).isEqualTo(0L); // INVALID
                    assertThat(bill.getFixedInterest()).isEqualTo(0L); // invalid
                })
                .verifyComplete();
    }

    @Test
    void shouldMapCsvRowToBillsObject_WithWhitespaceInNumericValues() {
        String csvData = buildCsvHeader() + "\n" +
                "CUST005,Semarang,Cabang Semarang,SPK-2024-005,Gedung E Lt 4,Kredit Investasi," +
                "Kartini Putri,Jl. Pahlawan No. 100 Semarang,4000000,9000000," +
                "2024-05-12,Lancar,0, 45000000 , 2200000 , 450000 , 900000 , 1350000 ," +
                " 420000 , 880000 , 1300000 , 46800000 , 380000 , 850000 , 45000 , 22000 ," +
                "AO-Semarang-005,dummy,Kios D, 800000 , 200000 ";

        mockWebServer.enqueue(new MockResponse()
                .setBody(csvData)
                .setHeader("Content-Type", "text/csv"));

        String url = mockWebServer.url("/bills.csv").toString();

        var result = downloadService.downloadAndParseCsv(url);

        StepVerifier.create(result)
                .assertNext(bill -> {
                    assertThat(bill.getCustomerId()).isEqualTo("CUST005");
                    assertThat(bill.getPlafond()).isEqualTo(45000000L);
                    assertThat(bill.getDebitTray()).isEqualTo(2200000L);
                    assertThat(bill.getInterest()).isEqualTo(450000L);
                    assertThat(bill.getTitipan()).isEqualTo(800000L);
                    assertThat(bill.getFixedInterest()).isEqualTo(200000L);
                })
                .verifyComplete();
    }

    @Test
    void shouldMapMultipleCsvRowsToBillsObjects() {
        String csvData = buildCsvHeader() + "\n" +
                "CUST006,Yogyakarta,Cabang Yogya,SPK-2024-006,Gedung F Lt 2,Kredit Konsumtif," +
                "Raden Ajeng,Jl. Malioboro No. 1 Yogya,3500000,7500000," +
                "2024-06-01,Lancar,0,35000000,1800000,350000,750000,1100000," +
                "330000,730000,1060000,36800000,320000,700000,35000,18000," +
                "AO-Yogya-006,dummy,Kios E,500000,150000\n" +
                "CUST007,Makassar,Cabang Makassar,SPK-2024-007,Gedung G Lt 3,Kredit Produktif," +
                "Hasanuddin,Jl. Sultan No. 25 Makassar,2500000,5500000," +
                "2024-07-15,Kurang Lancar,10,25000000,1300000,250000,550000,800000," +
                "230000,530000,760000,26300000,220000,500000,25000,13000," +
                "AO-Makassar-007,dummy,Kios F,300000,100000\n" +
                "CUST008,Palembang,Cabang Palembang,SPK-2024-008,Gedung H Lt 1,Kredit Jangka Pendek," +
                "Fatmawati,Jl. Veteran No. 50 Palembang,1800000,4500000," +
                "2024-08-20,Lancar,0,18000000,900000,180000,400000,580000," +
                "170000,390000,560000,18900000,160000,380000,18000,9000," +
                "AO-Palembang-008,dummy,Kios G,200000,80000";

        mockWebServer.enqueue(new MockResponse()
                .setBody(csvData)
                .setHeader("Content-Type", "text/csv"));

        String url = mockWebServer.url("/bills.csv").toString();

        var result = downloadService.downloadAndParseCsv(url).collectList();

        StepVerifier.create(result)
                .assertNext(bills -> {
                    assertThat(bills).hasSize(3);

                    assertThat(bills.get(0).getCustomerId()).isEqualTo("CUST006");
                    assertThat(bills.get(0).getName()).isEqualTo("Raden Ajeng");
                    assertThat(bills.get(0).getPlafond()).isEqualTo(35000000L);

                    assertThat(bills.get(1).getCustomerId()).isEqualTo("CUST007");
                    assertThat(bills.get(1).getName()).isEqualTo("Hasanuddin");
                    assertThat(bills.get(1).getPlafond()).isEqualTo(25000000L);

                    assertThat(bills.get(2).getCustomerId()).isEqualTo("CUST008");
                    assertThat(bills.get(2).getName()).isEqualTo("Fatmawati");
                    assertThat(bills.get(2).getPlafond()).isEqualTo(18000000L);
                })
                .verifyComplete();
    }
    private String buildCsvHeader() {
        return "customerId,wilayah,branch,noSpk,officeLocation,product,name,address,payDown,realization," +
                "dueDate,collectStatus,dayLate,plafond,debitTray,interest,principal,installment," +
                "lastInterest,lastPrincipal,lastInstallment,fullPayment,minInterest,minPrincipal," +
                "penaltyInterest,penaltyPrincipal,accountOfficer,dummy,kios,titipan,fixedInterest";
    }
}