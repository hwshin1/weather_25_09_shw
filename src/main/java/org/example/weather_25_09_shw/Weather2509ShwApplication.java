package org.example.weather_25_09_shw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class Weather2509ShwApplication {

    public static void main(String[] args) {
        SpringApplication.run(Weather2509ShwApplication.class, args);
    }

}
