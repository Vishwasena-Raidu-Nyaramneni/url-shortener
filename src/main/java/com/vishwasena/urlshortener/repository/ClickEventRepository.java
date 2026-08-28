package com.vishwasena.urlshortener.repository;

import com.vishwasena.urlshortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {
    List<ClickEvent> findByShortUrlId(Long shortUrlId);

    long countByShortUrlId(Long shortUrlId);

    Optional<ClickEvent> findFirstByShortUrlIdOrderByClickedAtDesc(Long shortUrlId);

    @Query("SELECT COUNT(DISTINCT ce.ipHash) FROM ClickEvent ce WHERE ce.shortUrl.id = :shortUrlId")
    long countUniqueVisitors(@Param("shortUrlId") Long shortUrlId);

    @Query("SELECT MAX(ce.clickedAt) FROM ClickEvent ce WHERE ce.shortUrl.id = :shortUrlId")
    OffsetDateTime getLastClickedAt(@Param("shortUrlId") Long shortUrlId);
}
