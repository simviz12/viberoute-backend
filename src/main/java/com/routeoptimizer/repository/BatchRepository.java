package com.routeoptimizer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.routeoptimizer.model.entity.Batch;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByCityIgnoreCase(String city);

    List<Batch> findByStatus(String status);

    List<Batch> findByCreationDate(LocalDate date);

    Optional<Batch> findFirstByStatusAndCityOrderByCreationDateAsc(String status, String city);

    List<Batch> findByCityAndDriverIsNullOrderByCreationDateAsc(String city);

    List<Batch> findByDriverIsNullOrderByCreationDateAsc();

    Optional<Batch> findByUuid(UUID uuid);
}
