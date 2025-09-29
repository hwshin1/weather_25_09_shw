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
public class ForecastZone extends BaseEntity {

    private String REG_ID;      // 예보구역코드
    private String TM_ST;       // 시작시각(년월일시분,KST)
    private String TM_ED;       // 종료시각(년월일시분,KST)
    private String REG_SP;      // (A:육상광역,B:육상국지,C:도시,D:산악,E:고속도로,H:해상광역,I:해상국지,J:연안바다,K:해수욕장,L:연안항로,M:먼항로,P:산악)
    private String REG_NAME;    // 예보구역명
}
