package com.example.medilingo.domain.drug.repository;

import com.example.medilingo.controller.drug.response.DrugCountDto;
import com.example.medilingo.controller.drug.response.WeeklyRankingRow;
import com.example.medilingo.domain.drug.DrugSearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DrugSearchLogRepository extends JpaRepository<DrugSearchLog, Long> {

    @Query("""
        select new com.example.medilingo.controller.drug.response.WeeklyRankingRow(
            l.localProductId,
            count(l.id)
        )
        from DrugSearchLog l
        where l.countryCode = :country
          and l.createdAt >= :from
          and l.createdAt < :to
        group by l.localProductId
        order by count(l.id) desc
    """)
    List<WeeklyRankingRow> weeklyRankingByCountry(
            @Param("country") String country,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}

