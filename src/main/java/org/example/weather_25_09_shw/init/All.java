package org.example.weather_25_09_shw.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.weather_25_09_shw.service.ForecastService;
import org.example.weather_25_09_shw.service.WeatherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class All {

    @Lazy
    @Autowired
    private All self;

    private final ForecastService forecastService;
    private final WeatherService weatherService;

    @Bean
    public ApplicationRunner allDev() {
        return args -> {
            log.info("초기 저장 시작");
            self.work1();
            self.work2();
            log.info("초기 저장 끝");
        };
    }

    @Transactional
    public void work1() {
        try {
            forecastService.save();
        } catch (Exception e) {
            log.error("초기 저장 실패");
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void work2() {
        try {
            int saved = weatherService.fetchSave();
            log.info("날씨 저장 완료 건수: {}", saved);
        } catch (Exception e) {
            log.error("날씨 저장 실패");
        }
    }
}
