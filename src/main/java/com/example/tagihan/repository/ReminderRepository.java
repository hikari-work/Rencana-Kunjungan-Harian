package com.example.tagihan.repository;

import com.example.tagihan.entity.Reminder;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReminderRepository extends ReactiveMongoRepository<Reminder, String> {
}
