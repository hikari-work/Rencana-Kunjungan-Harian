package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import com.example.tagihan.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;


    public Flux<Visit> findAll() {
        return visitRepository.findAll();
    }

    public Mono<Visit> save(Visit visit) {
        log.info("Saving visit: {}", visit);
        return visitRepository.save(visit);
    }
}