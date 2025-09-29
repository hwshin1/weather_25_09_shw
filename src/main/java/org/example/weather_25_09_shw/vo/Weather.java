package org.example.weather_25_09_shw.vo;

import jakarta.persistence.Entity;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
public class Weather extends BaseEntity {

    private String regId;               // 예보구역코드
    private String announceTime;        // 발표 시간
    private String numEf;               // 발효번호(발표시간기준)
    private String rnSt;                // 강수확률(%)
    private String temperature;         // 기온
    private String wf;                  // 날씨
    private String wfCd;                // 날씨코드
    private String wd1;                 // 풍향(1)
    private String wd2;                 // 풍향(2)
}
