package com.example.tagihan.service;

import com.example.tagihan.entity.Visit;
import com.example.tagihan.entity.VisitType;
import com.example.tagihan.repository.VisitRepository;
import com.example.tagihan.util.DateRangeUtil;
import com.example.tagihan.util.DateRangeUtil.DateRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;


    public Mono<Visit> findById(String id) {
        log.debug("Finding visit by id: {}", id);
        return visitRepository.findById(id);
    }

    public Mono<List<Visit>> findBySpk(String spk) {
        log.debug("Finding visits by spk: {}", spk);
        return visitRepository.findBySpk(spk);
    }


    public Mono<List<Visit>> findByAo(String ao) {
        log.debug("Finding visits by ao: {}", ao);
        return visitRepository.findByUserId(ao);
    }


    public Mono<List<Visit>> findByAoAndDate(String ao, String dateStr) {
        log.debug("Finding visits by ao: {} and date: {}", ao, dateStr);
        DateRange range = DateRangeUtil.createDayRange(dateStr);
        return findByAoAndDateRange(ao, range);
    }

    public Mono<List<Visit>> findByAoAndBetweenDate(String ao, String startDateStr, String endDateStr) {
        log.debug("Finding visits by ao: {} between {} and {}", ao, startDateStr, endDateStr);
        DateRange range = DateRangeUtil.createDateRange(startDateStr, endDateStr);
        return findByAoAndDateRange(ao, range);
    }

    public Mono<List<Visit>> findByAoAndDateBetweenOnVisitType(
            String ao,
            String startDateStr,
            String endDateStr,
            VisitType type) {
        log.debug("Finding visits by ao: {}, type: {} between {} and {}",
                ao, type, startDateStr, endDateStr);
        DateRange range = DateRangeUtil.createDateRange(startDateStr, endDateStr);
        return findByAoDateRangeAndType(ao, range, type);
    }

    private Mono<List<Visit>> findByAoAndDateRange(String ao, DateRange range) {
        return visitRepository.findByUserIdAndVisitDateBetween(
                ao,
                range.start(),
                range.end()
        );
    }

    private Mono<List<Visit>> findByAoDateRangeAndType(String ao, DateRange range, VisitType type) {
        return visitRepository.findByUserIdAndVisitTypeAndVisitDateBetween(
                ao,
                type,
                range.start(),
                range.end()
        );
    }
}