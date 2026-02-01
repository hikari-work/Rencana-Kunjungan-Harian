package com.example.tagihan.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Document(collection = "visit")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Visit {

    @Id
    private String id;

    private String userId;
    private VisitType visitType;

    @CreatedDate
    private Instant visitDate;
    private String spk;
    private String name;
    private String address;
    private Long debitTray;
    private Long interest;
    private Long principal;
    private Long plafond;
    private Long penalty;
    private String note;
    private String imageUrl;
    private Long appointment;
}
