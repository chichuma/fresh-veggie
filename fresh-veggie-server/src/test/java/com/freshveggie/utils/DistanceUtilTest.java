package com.freshveggie.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshveggie.exception.MapApiException;
import com.freshveggie.properties.BaiduProperties;
import com.freshveggie.properties.ShopProperties;
import com.freshveggie.utils.DistanceUtil.Coordinate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockStatic;

@DisplayName("DistanceUtil.distanceKm 直线距离计算")
class DistanceKmTest {

    @Test
    @DisplayName("同一点距离为 0")
    void samePoint_returnsZero() {
        Coordinate p = new Coordinate(39.9042, 116.4074);
        assertThat(DistanceUtil.distanceKm(p, p)).isZero();
    }

    @ParameterizedTest(name = "({0},{1}) -> ({2},{3}) 约 {4} km")
    @CsvSource({
            "39.9042, 116.4074, 31.2304, 121.4737, 1067",   // 北京 -> 上海
            "39.9042, 116.4074, 23.1291, 113.2644, 1888",   // 北京 -> 广州
            "39.909230, 116.397428, 39.9967, 116.4704, 11.5", // 门店 -> 望京SOHO
    })
    @DisplayName("已知点对的距离在 1% 误差内")
    void knownPairs(double lat1, double lng1, double lat2, double lng2, double expectedKm) {
        double actual = DistanceUtil.distanceKm(new Coordinate(lat1, lng1), new Coordinate(lat2, lng2));
        assertThat(actual).isCloseTo(expectedKm, withinPercentage(1));
    }

    @Test
    @DisplayName("距离对调起终点应相同")
    void symmetric() {
        Coordinate a = new Coordinate(39.9, 116.4);
        Coordinate b = new Coordinate(31.2, 121.5);
        assertThat(DistanceUtil.distanceKm(a, b)).isEqualTo(DistanceUtil.distanceKm(b, a));
    }

    @Test
    @DisplayName("经度差对应的距离应随纬度升高而缩短")
    void longitudeShrinksWithLatitude() {
        double atEquator = DistanceUtil.distanceKm(new Coordinate(0, 0), new Coordinate(0, 1));
        double atBeijing = DistanceUtil.distanceKm(new Coordinate(39.9, 116), new Coordinate(39.9, 117));
        assertThat(atBeijing / atEquator).isCloseTo(Math.cos(Math.toRadians(39.9)), within(0.001));
    }

    @Test
    @DisplayName("极值输入不应抛异常")
    void extremeInputs_doNotThrow() {
        assertThat(DistanceUtil.distanceKm(new Coordinate(0, 0), new Coordinate(0, 0))).isZero();
        assertThat(DistanceUtil.distanceKm(new Coordinate(90, 0), new Coordinate(-90, 0)))
                .isCloseTo(20015, withinPercentage(1));   // 半个地球周长
    }
}

@DisplayName("DistanceUtil.getJson 地图请求")
class DistanceJsonTest{
    private DistanceUtil distanceUtil;
    private MockedStatic<HttpClientUtil> mockedHttp;
    // 注意：这里原本放的是【真实的百度 AK】。它是未使用的死代码
    // （实际断言用的是下面的 TEST_AK/TEST_URL），但一旦提交就会永久留在
    // git 历史里 —— 已删除。需要真实 AK 时通过 BAIDU_AK 环境变量注入。
    private final String TEST_AK = "FreshVeggie";
    private final String TEST_URL = "https://test.com";
    Map<String, String> TEST_MAP = Map.of("AK", TEST_AK);


    @BeforeEach
    void setUp() {
        BaiduProperties baiduProperties = new BaiduProperties();
        ShopProperties shopProperties = new ShopProperties();
        shopProperties.getLocation().setLat(39.909230);
        shopProperties.getLocation().setLng(116.397428);
        distanceUtil = new DistanceUtil(new ObjectMapper(), baiduProperties, shopProperties);
        mockedHttp = mockStatic(HttpClientUtil.class);
    }
    @AfterEach
    void tearDown() {
        mockedHttp.close();
    }

    // 打桩辅助
    private void givenDoGetReturns(String body) {
        mockedHttp.when(() -> HttpClientUtil.doGet(anyString(), anyMap()))
                .thenReturn(body);
    }
    private void givenDoGetThrows(Exception e) {
        mockedHttp.when(() -> HttpClientUtil.doGet(anyString(), anyMap()))
                .thenAnswer(invocation -> { throw e; });
    }

    @Test
    @DisplayName("正常返回合法 JSON → 返回 JsonNode")
    void getJson() {
        givenDoGetReturns("{" +
                " \"code\" : 0 ," +
                " \"data\" :{" +
                    " \"lat\" : \"50\" ," +
                    " \"lng\" : \"60\" " +
                    "}" +
                "}");

        JsonNode node = distanceUtil.getJson(TEST_URL, TEST_MAP, "MOCK测试");

        assertEquals(0, node.get("code").asInt());
        assertEquals("50", node.get("data").get("lat").asText());
        assertEquals("60", node.get("data").get("lng").asText());

        mockedHttp.verify(() -> HttpClientUtil.doGet(eq(TEST_URL), eq(TEST_MAP)));
    }

    @Test
    @DisplayName("网络异常 → 包成 MapApiException 且保留 cause")
    void getJsonNetException() {
        UnknownHostException cause = new UnknownHostException(TEST_URL);
        givenDoGetThrows(cause);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.getJson(TEST_URL, TEST_MAP, "MOCK测试"));

        // 消息由 apiName 拼成：apiName + "接口调用失败: " + e
        assertTrue(ex.getMessage().contains("MOCK测试接口调用失败"), ex.getMessage());
        assertTrue(ex.getMessage().contains(TEST_URL), ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("接口无响应 → MapApiException")
    void getJsonNullBody() {
        givenDoGetReturns(null);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.getJson(TEST_URL, TEST_MAP, "MOCK测试"));

        assertTrue(ex.getMessage().contains("接口无响应"), ex.getMessage());
    }

    @Test
    @DisplayName("响应不是合法 JSON → MapApiException 且带原始 body")
    void getJsonInvalidJson() {
        givenDoGetReturns("<html>502 Bad Gateway</html>");

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.getJson(TEST_URL, TEST_MAP, "MOCK测试"));

        assertTrue(ex.getMessage().contains("接口响应不是合法 JSON"), ex.getMessage());
        assertTrue(ex.getMessage().contains("502 Bad Gateway"), ex.getMessage());
    }
}

@DisplayName("DistanceUtil.geocode 地址解析")
class GeocodeTest {

    // 望京 SOHO 附近，与门店(39.909230,116.397428)约 11.5 km
    private static final String OK_BODY = """
            {"status":0,"result":{"location":{"lng":116.4704,"lat":39.9967},
             "precise":1,"confidence":80,"comprehension":100,"level":"门址"}}
            """;

    private DistanceUtil distanceUtil;
    private MockedStatic<HttpClientUtil> mockedHttp;
    private final String TEST_AK = "test-ak";
    private final String TEST_URL = "https://test.com";

    @BeforeEach
    void setUp() {
        BaiduProperties baiduProperties = new BaiduProperties();
        baiduProperties.setAk(TEST_AK);
        baiduProperties.setLocationUrl(TEST_URL);

        ShopProperties shopProperties = new ShopProperties();
        shopProperties.getLocation().setLat(39.909230);
        shopProperties.getLocation().setLng(116.397428);

        distanceUtil = new DistanceUtil(new ObjectMapper(), baiduProperties, shopProperties);
        mockedHttp = mockStatic(HttpClientUtil.class);
    }
    @AfterEach
    void tearDown() {
        mockedHttp.close();
    }

    private void givenDoGetReturns(String body) {
        mockedHttp.when(() -> HttpClientUtil.doGet(anyString(), anyMap()))
                .thenReturn(body);
    }

    // ---------- 成功路径 ----------

    @Test
    @DisplayName("status=0 → 解析出经纬度，且请求参数带上了 address")
    void geocodeParsesLocation() {
        givenDoGetReturns(OK_BODY);

        DistanceUtil.Coordinate c = distanceUtil.geocode("北京市朝阳区望京SOHO");

        assertThat(c.lat()).isEqualTo(39.9967);
        assertThat(c.lng()).isEqualTo(116.4704);

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("ak", "test-ak");
        expected.put("address", "北京市朝阳区望京SOHO");
        expected.put("output", "json");
        mockedHttp.verify(() -> HttpClientUtil.doGet(eq(TEST_URL), eq(expected)));
    }

    @Test
    @DisplayName("端到端：地址 → 距离，门店到望京SOHO 约 11.5 km")
    void getDistanceEndToEnd() {
        givenDoGetReturns(OK_BODY);

        assertThat(distanceUtil.getDistance("北京市朝阳区望京SOHO"))
                .isCloseTo(11.545, within(0.05));
    }

    // ---------- 失败路径：这块是 getJson 测不到的 ----------

    @Test
    @DisplayName("status=210 且 result 为空数组 → MapApiException 带状态码")
    void geocodeStatusNotZero() {
        givenDoGetReturns("""
                {"status":210,"message":"APP IP校验失败","result":[]}
                """);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.geocode("北京市朝阳区望京SOHO"));

        assertTrue(ex.getMessage().contains("status=210"), ex.getMessage());
        assertTrue(ex.getMessage().contains("APP IP校验失败"), ex.getMessage());
    }

    @Test
    @DisplayName("status=240（AK 被拒）也要被拦下")
    void geocodeAkRejected() {
        givenDoGetReturns("""
                {"status":240,"message":"APP 服务被禁用","result":[]}
                """);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.geocode("北京市朝阳区望京SOHO"));

        assertTrue(ex.getMessage().contains("status=240"), ex.getMessage());
    }

    @Test
    @DisplayName("status=0 但 result 为空数组 → 未返回有效坐标")
    void geocodeEmptyResult() {
        givenDoGetReturns("""
                {"status":0,"result":[]}
                """);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.geocode("北京市朝阳区望京SOHO"));

        assertTrue(ex.getMessage().contains("未返回有效坐标"), ex.getMessage());
    }

    @Test
    @DisplayName("status=0 但 location 缺字段 → 未返回有效坐标（NaN 兜底）")
    void geocodeLocationMissingField() {
        givenDoGetReturns("""
                {"status":0,"result":{"location":{"lat":39.9967}}}
                """);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.geocode("北京市朝阳区望京SOHO"));

        assertTrue(ex.getMessage().contains("未返回有效坐标"), ex.getMessage());
    }

    @Test
    @DisplayName("status=0 但坐标不是数字 → 未返回有效坐标")
    void geocodeNonNumericCoordinate() {
        givenDoGetReturns("""
                {"status":0,"result":{"location":{"lat":"abc","lng":"116.4704"}}}
                """);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.geocode("北京市朝阳区望京SOHO"));

        assertTrue(ex.getMessage().contains("未返回有效坐标"), ex.getMessage());
    }

    @Test
    @DisplayName("坐标越界 → 越界坐标异常")
    void geocodeOutOfRangeCoordinate() {
        givenDoGetReturns("""
                {"status":0,"result":{"location":{"lat":999,"lng":116.4704}}}
                """);

        MapApiException ex = assertThrows(MapApiException.class,
                () -> distanceUtil.geocode("北京市朝阳区望京SOHO"));

        assertTrue(ex.getMessage().contains("越界坐标"), ex.getMessage());
    }

    @Test
    @DisplayName("顶层没有 status 字段 → 当前按 status=0 放行")
    void geocodeMissingStatusIsTreatedAsSuccess() {
        givenDoGetReturns("""
                {"result":{"location":{"lat":39.9967,"lng":116.4704}}}
                """);

        assertThat(distanceUtil.geocode("北京市朝阳区望京SOHO").lat()).isEqualTo(39.9967);
    }
}