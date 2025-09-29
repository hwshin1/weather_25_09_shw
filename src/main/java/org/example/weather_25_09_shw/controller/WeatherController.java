package org.example.weather_25_09_shw.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.weather_25_09_shw.service.ForecastService;
import org.example.weather_25_09_shw.service.WeatherService;
import org.example.weather_25_09_shw.vo.Weather;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/weather")
public class WeatherController {

    private final WeatherService weatherService;
    private final ForecastService forecastService;

    @GetMapping("/main")
    public String showMainPage(@RequestParam(value = "city", required = false) String city, Model model) {

        if (city == null || city.isBlank()) {
            return "home/main";
        }

        String regId = forecastService.getREG_ID(city);

        if (regId == null) {
            model.addAttribute("error", "도시 찾기 실패");
            return "home/main";
        }

        Weather weather = weatherService.getByRegId(regId);

        if (weather == null) {
            model.addAttribute("error", "해당 지역(regId=" + regId + ")의 날씨 데이터가 아직 없습니다.");
            model.addAttribute("city", city);
            model.addAttribute("regId", regId);
            return "home/main";
        }

        model.addAttribute("city", city);
        model.addAttribute("regId", regId);
        model.addAttribute("weather", weather);

        return "home/main";
    }
}
