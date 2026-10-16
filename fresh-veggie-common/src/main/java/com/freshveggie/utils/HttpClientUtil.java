package com.freshveggie.utils;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.net.URI;
import java.util.Map;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class HttpClientUtil {

    static final int TIMEOUT_MSEC = 5 * 1000;

    /**
     * 全局唯一的 HttpClient
     * 可以安全地被多个线程共享
     */
    private static final CloseableHttpClient HTTP_CLIENT = buildHttpClient();

    private static CloseableHttpClient buildHttpClient() {
        // 连接池：限制总连接数与单路由（同一目标主机）连接数
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(200);              // 整个池最多 200 条连接
        cm.setDefaultMaxPerRoute(50);     // 到同一个 host（比如 api.map.baidu.com）最多 50 条
        cm.setValidateAfterInactivity(10_000);  // 空闲 10s 后复用时校验连接是否还活着

        // 请求配置：连接/读取/等待全部设超时
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MSEC)          // 建立 TCP 连接的超时
                .setConnectionRequestTimeout(TIMEOUT_MSEC)// 从池里等一条空闲连接的超时
                .setSocketTimeout(TIMEOUT_MSEC)           // 等响应的超时
                .build();

        return HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(config)
                .build();
    }

    /** 发送GET方式请求 */
    public static String doGet(String url, Map<String, String> paramMap) {
        try {
            URIBuilder builder = new URIBuilder(url);
            if (paramMap != null) {
                for (String key : paramMap.keySet()) {
                    builder.addParameter(key, paramMap.get(key));
                }
            }
            URI uri = builder.build();
            HttpGet httpGet = new HttpGet(uri);

            // 这里只关 response，不关 httpClient
            try (CloseableHttpResponse response = HTTP_CLIENT.execute(httpGet)) {
                if (response.getStatusLine().getStatusCode() == 200) {
                    return EntityUtils.toString(response.getEntity(), "UTF-8");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("GET 请求失败: " + url, e);
        }
        return null;
    }
}