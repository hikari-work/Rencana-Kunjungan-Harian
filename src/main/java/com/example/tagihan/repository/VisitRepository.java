package com.example.tagihan.repository;

import com.example.tagihan.entity.Visit;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitRepository extends ReactiveMongoRepository<Visit, String> {
}
