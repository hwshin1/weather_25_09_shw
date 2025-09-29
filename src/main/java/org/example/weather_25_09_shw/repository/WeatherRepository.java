package org.example.weather_25_09_shw.repository;

import org.example.weather_25_09_shw.vo.Weather;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeatherRepository extends JpaRepository<Weather, Long> {
    Optional<Weather> findByRegIdAndAnnounceTime(String regId, String announceTime);

    Optional<Weather> findTopByRegIdOrderByAnnounceTimeDesc(String regId);
}
