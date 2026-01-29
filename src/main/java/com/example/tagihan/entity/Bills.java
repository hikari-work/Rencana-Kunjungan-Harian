package com.example.tagihan.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "bills")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Bills {

    /**
     * The unique identifier of the customer.
     */
    private String customerId;
    /**
     * The region (wilayah) of the customer.
     */
    private String wilayah;
    /**
     * The branch code.
     */
    private String branch;
    /**
     * The SPK (Surat Perintah Kerja) number, acting as the primary key.
     */
    @Id
    private String noSpk;
    /**
     * The location of the office handling the bill.
     */
    private String officeLocation;
    /**
     * The name of the product associated with the bill.
     */
    private String product;
    /**
     * The name of the customer.
     */
    private String name;
    /**
     * The address of the customer.
     */
    private String address;
    /**
     * Payment down status or amount.
     */
    private String payDown;
    /**
     * Realization status or amount.
     */
    private String realization;
    /**
     * The due date of the bill.
     */
    private String dueDate;
    /**
     * The collection status of the bill.
     */
    private String collectStatus;
    /**
     * The number of days late.
     */
    private String dayLate;
    /**
     * The plafond (credit ceiling) amount.
     */
    private Long plafond;
    /**
     * The remaining debit tray amount.
     */
    private Long debitTray;
    /**
     * The interest amount.
     */
    private Long interest;
    /**
     * The principal amount.
     */
    private Long principal;
    /**
     * The installment amount.
     */
    private Long installment;
    /**
     * The last interest amount paid.
     */
    private Long lastInterest;
    /**
     * The last principal amount paid.
     */
    private Long lastPrincipal;
    /**
     * The last installment amount paid.
     */
    private Long lastInstallment;
    /**
     * The full payment amount required for settlement.
     */
    private Long fullPayment;
    /**
     * The minimum interest payment required.
     */
    private Long minInterest;
    /**
     * The minimum principal payment required.
     */
    private Long minPrincipal;
    /**
     * The penalty interest amount.
     */
    private Long penaltyInterest;
    /**
     * The penalty principal amount.
     */
    private Long penaltyPrincipal;
    /**
     * The account officer assigned to this bill.
     */
    private String accountOfficer;
    /**
     * The kios associated with the bill.
     */
    private String kios;
    /**
     * Any entrusted funds (titipan).
     */
    private Long titipan;
    /**
     * The fixed interest amount.
     */
    private Long fixedInterest;
}