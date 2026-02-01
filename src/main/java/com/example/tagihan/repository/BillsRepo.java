package com.example.tagihan.repository;

import com.example.tagihan.entity.Bills;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface BillsRepo extends ReactiveMongoRepository<Bills, String> {
    Mono<Bills> findByNoSpk(String noSpk);
}
