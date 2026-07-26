package com.novax.leadora.infrastructure.persistence.repository;

import com.novax.leadora.infrastructure.persistence.entity.SlaHolidayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface SlaHolidayRepository extends JpaRepository<SlaHolidayEntity, LocalDate> {
}
