package org.example.weather_25_09_shw.repository;

import org.example.weather_25_09_shw.vo.ForecastZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ForecastRepository extends JpaRepository<ForecastZone, Long> {

    @Query("SELECT f.REG_ID FROM ForecastZone f WHERE f.REG_SP = 'c'")
    List<String> findByREG_IDAndREG_SP();

    @Query("SELECT f.REG_ID FROM ForecastZone f WHERE f.REG_NAME = :city AND f.REG_SP = 'c'")
    Optional<String> findByREG_ID(@Param("city") String city);
}
