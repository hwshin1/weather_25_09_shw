package org.example.weather_25_09_shw.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.weather_25_09_shw.repository.ForecastRepository;
import org.example.weather_25_09_shw.vo.ForecastZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastService {

    @Value("${kma.apiKey}")
    private String apiKey;

    private final ForecastRepository forecastRepository;

    public long count() {
        return forecastRepository.count();
    }

    // API 호출
    private String apiCode() throws Exception {
        String urlStr = "https://apihub.kma.go.kr/api/typ01/url/fct_shrt_reg.php?tmfc=0&authKey=" + apiKey;

        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);

        int code = con.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();

        // 주석: 기본은 EUC-KR, 실패 시 UTF-8로 재시도
        String body = readAll(is, Charset.forName("EUC-KR"));
        if (body.isEmpty() || isLikelyGarbled(body)) {
            body = readAll(con.getInputStream(), StandardCharsets.UTF_8);
        }

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("KMA API error: httpStatus=" + code + ", preview=" + preview(body, 1));
        }
        return body;
    }

    @Transactional
    public void save() throws Exception {
        if (count() > 0) return;

        String csv = apiCode();
        List<ForecastZone> bulk = new ArrayList<>(2048);

        // CR/LF 모두 처리
        String[] lines = csv.split("\\r?\\n", -1);

        for (String rawLine : lines) {
            String line = sanitizeLine(rawLine);
            if (line.isEmpty()) continue;
            if (isHeaderOrComment(line)) continue;

            ForecastZone zone = parseToZone(line);
            if (zone == null) continue; // 파싱 실패 라인 스킵

            bulk.add(zone);

            // 대용량 대비 배치 저장
            if (bulk.size() >= 1000) {
                forecastRepository.saveAllAndFlush(bulk);
                bulk.clear();
            }
        }

        if (!bulk.isEmpty()) {
            forecastRepository.saveAllAndFlush(bulk);
        }
    }

    // 한 줄을 엔티티로 변환
    private ForecastZone parseToZone(String line) {
        // 다중 공백 기준 분할
        String[] cols = line.trim().split("\\s+");
        if (cols.length < 5) return null;

        String regId = cols[0];
        String tmSt  = cols[1];
        String tmEd  = cols[2];
        String regSp = cols[3];

        // REG_NAME은 공백 포함 가능
        StringBuilder name = new StringBuilder();
        for (int i = 4; i < cols.length; i++) {
            if (i > 4) name.append(' ');
            name.append(cols[i]);
        }
        String regName = name.toString().trim();

        // 기본 유효성
        if (regId.isEmpty() || tmSt.isEmpty() || tmEd.isEmpty() || regSp.isEmpty() || regName.isEmpty()) {
            return null;
        }

        return ForecastZone.builder()
                .REG_ID(regId)
                .TM_ST(tmSt)
                .TM_ED(tmEd)
                .REG_SP(regSp)
                .REG_NAME(regName)
                .build();
    }

    // 헤더 라인 식별(선행 공백 제거 후 비교)
    private boolean isHeaderOrComment(String line) {
        String s = line.trim();
        if (s.isEmpty()) return true;
        if (s.startsWith("#")) return true;

        // 첫 토큰 기준으로 판정(대소문자 무시)
        String first = s.split("\\s+", 2)[0].toUpperCase();
        return first.equals("REG_ID") || first.equals("REG_NAME")
                || first.equals("TM_ST") || first.equals("TM_ED")
                || first.equals("REG_SP");
    }

    // BOM, 제어문자, 탭 등을 정리
    private String sanitizeLine(String line) {
        if (line == null) return "";
        // BOM 제거
        String s = line.replace("\uFEFF", "");
        // 탭을 공백으로
        s = s.replace('\t', ' ');
        // 제어문자 제거(개행 제외)
        s = s.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
        return s.trim();
    }

    // 입력 스트림 전체 읽기
    private String readAll(InputStream is, Charset cs) throws Exception {
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, cs))) {
            StringBuilder sb = new StringBuilder(8192);
            String l;
            while ((l = br.readLine()) != null) {
                sb.append(l).append('\n');
            }
            return sb.toString();
        }
    }

    private boolean isLikelyGarbled(String s) {
        int len = Math.min(s.length(), 500);
        String head = s.substring(0, len);
        return head.contains("???");
    }

    private String preview(String s, int lines) {
        if (s == null) return "";
        String[] arr = s.split("\\r?\\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(lines, arr.length); i++) {
            sb.append(arr[i]);
            if (i < lines - 1) sb.append('\n');
        }
        return sb.toString();
    }

    // 도시 이름으로 regId 찾기
    public String getREG_ID(String city) {
        if (city.isEmpty()) return null;
        return forecastRepository.findByREG_ID(city).orElse(null);
    }
}
