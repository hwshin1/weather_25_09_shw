let map;
let currentHighlight = null;
let sggPolygons = [];
let sggOverlays = [];
let currentLevel = 9;

/* ===================== 시군구 이름 헬퍼 ===================== */
function getSggName(p) {
    return p?.SIG_KOR_NM || p?.SIGUNGU_NM || p?.sgg_nm || null;
}

/* ===================== 초기화 ===================== */
document.addEventListener("DOMContentLoaded", () => {
    kakao.maps.load(() => {
        map = new kakao.maps.Map(document.getElementById("map"), {
            center: new kakao.maps.LatLng(37.5665, 126.9780),
            level: currentLevel
        });

        // 호출 시그니처 맞추기: (url, strokeColor)
        loadSggPolygons("/TL_SCCO_SIG.json", "#004C80").then(showAllSgg);

        // 줌 변경 시에도 시군구만 계속 노출
        kakao.maps.event.addListener(map, "zoom_changed", () => {
            currentLevel = map.getLevel();
            showAllSgg();
            if (currentLevel !== 9) map.setLevel(9, { animate: false });
        });

        // 지도 클릭 핸들러
        kakao.maps.event.addListener(map, "click", (evt) => handleMapClick(evt.latLng));
    });
});

/* ===================== 지도 클릭 처리 ===================== */
function handleMapClick(latLng) {
    const hit = pickSggByPoint(latLng);
    if (!hit) return;

    // 하이라이트
    highlightPolygon(hit.path);

    // 레벨 9 유지
    if (map.getLevel() !== 9) {
        map.setLevel(9, { animate: false });
    }

    // 패널 라벨 갱신 (제공된 HTML의 #emdName 사용)
    const sggNm = getSggName(hit.properties) || "";
    setPanelSelectedLabel(`${sggNm} 선택됨`);

    // 로딩 표시 후 데이터 요청
    renderWeatherLoading();

    const q = new URLSearchParams();
    q.set("city", sggNm);
    q.set("lat", latLng.getLat().toString());
    q.set("lng", latLng.getLng().toString());

    // /weather/main 을 JSON으로 호출 (컨트롤러에서 produces=application/json 분기 필요)
    fetch(`/weather/main?${q.toString()}`, { headers: { Accept: "application/json" } })
        .then(async (res) => {
            const ct = res.headers.get("content-type") || "";
            if (!res.ok) throw new Error("요청 실패");
            if (!ct.includes("application/json")) throw new Error("JSON 아님");
            return res.json();
        })
        .then((data) => renderWeatherCard(data))
        .catch(() => renderWeatherError("날씨 정보를 불러오지 못했습니다."));
}

/* ===================== 폴리곤 로딩/표시 ===================== */
function loadSggPolygons(url, strokeColor) {
    return fetch(url)
        .then((r) => r.json())
        .then((geojson) => {
            geojson.features.forEach((f) => {
                const g = f.geometry;
                const p = f.properties;

                const multi = g.type === "Polygon" ? [g.coordinates] : g.coordinates;
                multi.forEach((poly) => {
                    const coords = poly[0];
                    const path = coords.map((c) => new kakao.maps.LatLng(c[1], c[0]));

                    // 배경 폴리곤: 드래그 방해 방지
                    const polygon = new kakao.maps.Polygon({
                        path,
                        strokeWeight: 2,
                        strokeColor,
                        strokeOpacity: 0.8,
                        strokeStyle: "solid",
                        fillColor: "rgba(102,179,255,0)",
                        fillOpacity: 0.08,
                        clickable: false,
                        zIndex: 1
                    });

                    // 라벨: 드래그 방해 방지
                    const center = getPathCenter(path);
                    const label = document.createElement("div");
                    label.textContent = getSggName(p) || "";
                    label.style.cssText = [
                        "background:#fff",
                        "border:1px solid #444",
                        "padding:3px 6px",
                        "font-size:12px",
                        "border-radius:4px",
                        "pointer-events:none"
                    ].join(";");

                    const overlay = new kakao.maps.CustomOverlay({
                        content: label,
                        position: center,
                        yAnchor: 1,
                        zIndex: 2
                    });

                    sggPolygons.push({ path, properties: p, polygon, overlay });
                    sggOverlays.push(overlay);
                });
            });
        });
}

function showAllSgg() {
    clearHighlight();
    sggPolygons.forEach(({ polygon }) => polygon.setMap(map));
    sggOverlays.forEach((ov) => ov.setMap(map));
}

function clearHighlight() {
    if (currentHighlight) {
        currentHighlight.setMap(null);
        currentHighlight = null;
    }
}

function highlightPolygon(path) {
    clearHighlight();

    currentHighlight = new kakao.maps.Polygon({
        map,
        path,
        strokeWeight: 2,
        strokeColor: "#004c80",
        strokeOpacity: 0.9,
        fillColor: "#00a0e9",
        fillOpacity: 0.25,
        clickable: false,
        zIndex: 3
    });
}

/* ===================== 공간 판별/유틸 ===================== */
function pickSggByPoint(latLng) {
    for (let i = 0; i < sggPolygons.length; i++) {
        const { path, properties } = sggPolygons[i];
        if (pointInPolygon(latLng, path)) {
            return { path, properties };
        }
    }
    return null;
}

function pointInPolygon(latLng, path) {
    let x = latLng.getLng(), y = latLng.getLat(), inside = false;
    for (let i = 0, j = path.length - 1; i < path.length; j = i++) {
        const xi = path[i].getLng(), yi = path[i].getLat();
        const xj = path[j].getLng(), yj = path[j].getLat();
        const intersect =
            ((yi > y) !== (yj > y)) &&
            (x < (((xj - xi) * (y - yi)) / ((yj - yi) || 1e-10) + xi));
        if (intersect) inside = !inside;
    }
    return inside;
}

function getPathCenter(path) {
    const lat = path.reduce((s, p) => s + p.getLat(), 0) / path.length;
    const lng = path.reduce((s, p) => s + p.getLng(), 0) / path.length;
    return new kakao.maps.LatLng(lat, lng);
}

/* ===================== 패널/날씨 연동 (제공 마크업 기준) ===================== */
// 패널 라벨: #emdName
function setPanelSelectedLabel(msg) {
    const el = document.getElementById("emdName");
    if (el) el.textContent = msg || "도시를 검색해 주세요";
}

// 로딩 UI
function renderWeatherLoading() {
    const wrap = document.getElementById("card-floating");
    if (!wrap) return;
    wrap.innerHTML = `
    <div class="border rounded p-4 space-y-2">
      <div class="text-gray-600 text-sm">날씨 정보를 불러오는 중입니다...</div>
      <div class="animate-pulse grid grid-cols-3 gap-3">
        <div class="border rounded p-3">
          <div class="h-3 bg-gray-200 rounded mb-2"></div>
          <div class="h-5 bg-gray-200 rounded"></div>
        </div>
        <div class="border rounded p-3">
          <div class="h-3 bg-gray-200 rounded mb-2"></div>
          <div class="h-5 bg-gray-200 rounded"></div>
        </div>
        <div class="border rounded p-3">
          <div class="h-3 bg-gray-200 rounded mb-2"></div>
          <div class="h-5 bg-gray-200 rounded"></div>
        </div>
      </div>
    </div>
  `;
}

// 성공 UI (서버 JSON 스키마에 맞춰 표시)
function renderWeatherCard(w) {
    const wrap = document.getElementById("card-floating");
    if (!wrap) return;

    const asStr = (v, unit = "") => (v === null || v === undefined || v === "" ? "-" : `${v}${unit}`);
    const city = w.city || "-";
    const regId = w.regId || "-";
    const announce = w.announceTime || "-";
    const temp = asStr(w.temperature, " ℃");
    const rnSt = asStr(w.rnSt, " %");
    const wd1 = w.wd1 || "-";
    const wf = w.wf || "-";

    wrap.innerHTML = `
    <div class="border rounded p-4 space-y-2">
      <div class="text-gray-600 text-sm">
        지역: <span>${city}</span>
        (<span>${regId}</span>) · 업데이트: <span>${announce}</span>
      </div>
      <div class="grid grid-cols-3 gap-3">
        <div class="border rounded p-3">
          <div class="text-xs text-gray-500">온도</div>
          <div class="text-xl font-semibold">${temp}</div>
        </div>
        <div class="border rounded p-3">
          <div class="text-xs text-gray-500">강수확률(습도)</div>
          <div class="text-xl font-semibold">${rnSt}</div>
        </div>
        <div class="border rounded p-3">
          <div class="text-xs text-gray-500">풍향</div>
          <div class="text-xl font-semibold">${wd1}</div>
        </div>
      </div>
      <div class="text-gray-700">${wf}</div>
    </div>
  `;
}

// 에러 UI
function renderWeatherError(msg) {
    const wrap = document.getElementById("card-floating");
    if (!wrap) return;
    wrap.innerHTML = `<div class="text-red-600">${msg || "에러"}</div>`;
}
