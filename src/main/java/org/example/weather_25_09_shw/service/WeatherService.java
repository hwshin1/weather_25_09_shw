package org.example.weather_25_09_shw.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.weather_25_09_shw.repository.ForecastRepository;
import org.example.weather_25_09_shw.repository.WeatherRepository;
import org.example.weather_25_09_shw.vo.Weather;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherService {

    @Value("${kma.apiKey}")
    private String apiKey;

    private final WeatherRepository weatherRepository;
    private final ForecastRepository forecastRepository;

    public long count() {
        return weatherRepository.count();
    }

    @Transactional
    public int fetchSave() {
        if (count() > 0) return 0;

        List<String> regIds = forecastRepository.findByREG_IDAndREG_SP();
        int totalSaved = 0;

        for (String regId : regIds) {
            try {
                String xml = apiCode(regId);
                if (xml == null || xml.isBlank()) {
                    log.warn("응답 본문이 비어 있음: regId={}", regId);
                    continue;
                }

                List<Weather> parsed = parseXml(xml, regId);

                List<Weather> toSave = new ArrayList<>(parsed.size());
                for (Weather w : parsed) {
                    boolean exists = weatherRepository.findByRegIdAndAnnounceTime(w.getRegId(), w.getAnnounceTime()).isPresent();
                    if (!exists) toSave.add(w);
                }

                if (!toSave.isEmpty()) {
                    weatherRepository.saveAllAndFlush(toSave);
                    totalSaved += toSave.size();
                    log.info("저장 완료(XML): regId={}, 신규 {}건", regId, toSave.size());
                } else {
                    log.info("신규 데이터 없음(XML): regId={}, 응답 {}건", regId, parsed.size());
                }
            } catch (Exception e) {
                log.warn("regId={} 저장 중 오류, 건너뜀: {}", regId, e.getMessage());
            }
        }
        return totalSaved;
    }

    private String apiCode(String regId) {
        String urlStr = "https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstMsgService/getLandFcst"
                + "?pageNo=1"
                + "&numOfRows=10"
                + "&dataType=XML"
                + "&regId=" + regId
                + "&authKey=" + apiKey;

        try {
            URL url = new URL(urlStr);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String inputLine;
            StringBuffer xmlContent = new StringBuffer();

            // 서버로부터 받은 데이터를 줄단위로 읽습니다.
            while ((inputLine = in.readLine()) != null) {
                xmlContent.append(inputLine);
            }
            // BufferedReader를 닫습니다.
            in.close();
            // HttpURLConnection을 닫습니다.
            conn.disconnect();
            return xmlContent.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private List<Weather> parseXml(String xml, String regId) throws Exception {
        List<Weather> list = new ArrayList<>();

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        Document doc = dbf.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        doc.getDocumentElement().normalize();

        String resultCode = getSingleText(doc, "resultCode");
        String resultMsg  = getSingleText(doc, "resultMsg");
        if (resultCode != null && !"00".equals(resultCode)) {
            log.warn("KMA 오류(XML): code={}, msg={}", resultCode, resultMsg);
            return list;
        }

        NodeList items = doc.getElementsByTagName("item");
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < items.getLength(); i++) {
            Element e = (Element) items.item(i);

            String announceTime = getChildText(e, "announceTime");
            String numEf = getChildText(e, "numEf");
            if (announceTime == null || announceTime.isBlank()) continue;

            String uniq = announceTime + "_" + (numEf == null ? "" : numEf);
            if (!seen.add(uniq)) continue;

            String rnSt = getChildText(e, "rnSt");
            String ta   = getChildText(e, "ta");
            String wf   = getChildText(e, "wf");
            String wfCd = getChildText(e, "wfCd");
            String wd1  = getChildText(e, "wd1");
            String wd2  = getChildText(e, "wd2");

            Weather w = Weather.builder()
                    .regId(regId)
                    .announceTime(nvl(announceTime))
                    .numEf(nvl(numEf))
                    .rnSt(nvl(rnSt))
                    .temperature(nvl(ta))
                    .wf(nvl(wf))
                    .wfCd(nvl(wfCd))
                    .wd1(nvl(wd1))
                    .wd2(nvl(wd2))
                    .build();

            list.add(w);
        }

        list.sort((a, b) -> {
            int cmp = b.getAnnounceTime().compareTo(a.getAnnounceTime());
            if (cmp != 0) return cmp;
            String x = a.getNumEf() == null ? "" : a.getNumEf();
            String y = b.getNumEf() == null ? "" : b.getNumEf();
            return x.compareTo(y);
        });

        return list;
    }

    // 유틸
    private String getSingleText(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent().trim();
    }

    private String getChildText(Element e, String tag) {
        NodeList nl = e.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent().trim();
    }

    private String nvl(String s) { return s == null ? "" : s; }

    // regId로 날씨 찾기
    public Weather getByRegId(String regId) {
        if (regId == null) return null;
        return weatherRepository.findTopByRegIdOrderByAnnounceTimeDesc(regId).orElse(null);
    }
}
