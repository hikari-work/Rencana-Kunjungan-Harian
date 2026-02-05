package com.example.tagihan.repository;

import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Repository
public interface VisitRepository extends ReactiveMongoRepository<Visit, String> {
    Mono<List<Visit>> findBySpk(String spk);

    Flux<Visit> findByUserId(String userId);

    Mono<List<Visit>> findByUserIdAndVisitDate(String userId, Instant visitDate);

    Mono<List<Visit>> findByUserIdAndVisitDateBetween(String userId, Instant visitDateAfter, Instant visitDateBefore);

    Mono<List<Visit>> findByUserIdAndVisitTypeAndVisitDateBetween(String userId, VisitType visitType, Instant visitDateAfter, Instant visitDateBefore);
}
