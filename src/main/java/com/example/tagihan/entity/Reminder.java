package com.example.tagihan.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "reminder")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Reminder {

    @Id
    private String id;

    private String visitId;
    private LocalDate time;

}
