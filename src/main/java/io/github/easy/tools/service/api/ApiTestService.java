package io.github.easy.tools.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import io.github.easy.tools.entity.api.ApiInfo;
import io.github.easy.tools.ui.config.ApiTestConfigState;
import io.github.easy.tools.utils.NotificationUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p> API 测试服务 </p>
 * <p>
 * 负责执行API测试请求，自动合并公共请求头，并在需要时执行前置登录请求以缓存Token。
 * </p>
 */
public class ApiTestService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 执行API请求（自动处理前置登录与公共头）
     *
     * @param project 项目
     * @param apiInfo API信息
     * @param customHeaders 额外临时头（来自面板输入）
     * @param bodyJson 请求体（JSON，可为空）
     * @return 响应文本
     */
    public String execute(Project project, ApiInfo apiInfo, Map<String, String> customHeaders, String bodyJson) {
        try {
            ApiTestConfigState cfg = ApiTestConfigState.getInstance(project);
            Map<String, String> headers = new HashMap<>();
            // 合并公共头
            this.addHeaders(headers, cfg.commonHeaders);
            // 合并临时头
            if (customHeaders != null) {
                headers.putAll(customHeaders);
            }

            // 前置登录请求
            TokenCacheService tokenCache = ApplicationManager.getApplication().getService(TokenCacheService.class);
            String token = tokenCache.getValidToken();
            if (token == null && cfg.preRequestEnabled) {
                token = this.executePreRequestAndCache(cfg, tokenCache);
            }
            // 将Token挂载到头
            if (token != null && cfg.preRequest != null && cfg.preRequest.getTokenHeaderName() != null) {
                String headerName = cfg.preRequest.getTokenHeaderName();
                String value = cfg.preRequest.isUseBearer() ? "Bearer " + token : token;
                headers.put(headerName, value);
            }

            String url = this.buildAbsoluteUrl(cfg.baseUrl, apiInfo.getUrl());
            HttpRequest request = this.buildRequest(url, apiInfo.getMethod(), headers, bodyJson);
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (Exception e) {
            NotificationUtil.showError(project, "API请求失败: " + e.getMessage());
            return "";
        }
    }

    private String executePreRequestAndCache(ApiTestConfigState cfg, TokenCacheService tokenCache) throws Exception {
        ApiTestConfigState.PreRequestConfig pre = cfg.preRequest;
        if (pre == null || pre.getUrl() == null || pre.getUrl().isEmpty()) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        this.addHeaders(headers, pre.getHeaders());
        String url = this.buildAbsoluteUrl(cfg.baseUrl, pre.getUrl());
        HttpRequest request = this.buildRequest(url, pre.getMethod(), headers, pre.getBodyJson());
        HttpResponse<String> resp = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String token = this.extractToken(resp.body(), pre.getTokenJsonPath());
        if (token != null && !token.isEmpty()) {
            long expireAt = Instant.now().plus(Duration.ofMinutes(pre.getTokenExpireMinutes())).toEpochMilli();
            tokenCache.setToken(token, expireAt);
        }
        return token;
    }

    private void addHeaders(Map<String, String> target, List<ApiTestConfigState.HeaderItem> items) {
        if (items == null) {
            return;
        }
        for (ApiTestConfigState.HeaderItem item : items) {
            if (item == null) {
                continue;
            }
            if (item.getName() != null && !item.getName().isEmpty()) {
                target.put(item.getName(), item.getValue() == null ? "" : item.getValue());
            }
        }
    }

    private String buildAbsoluteUrl(String baseUrl, String path) {
        if (path == null) {
            return baseUrl;
        }
        if (baseUrl == null || baseUrl.isEmpty() || path.startsWith("http")) {
            return path;
        }
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = path.startsWith("/") ? path : "/" + path;
        return b + p;
    }

    private HttpRequest buildRequest(String url, String method, Map<String, String> headers, String bodyJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60));
        if (headers != null) {
            headers.forEach(builder::header);
        }
        String m = method == null ? "GET" : method.toUpperCase();
        if ("GET".equals(m)) {
            builder.GET();
        } else if ("POST".equals(m)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(bodyJson == null ? "" : bodyJson));
        } else if ("PUT".equals(m)) {
            builder.header("Content-Type", "application/json");
            builder.PUT(HttpRequest.BodyPublishers.ofString(bodyJson == null ? "" : bodyJson));
        } else if ("DELETE".equals(m)) {
            if (bodyJson != null && !bodyJson.isEmpty()) {
                builder.method("DELETE", HttpRequest.BodyPublishers.ofString(bodyJson));
            } else {
                builder.DELETE();
            }
        } else if ("PATCH".equals(m)) {
            builder.header("Content-Type", "application/json");
            builder.method("PATCH", HttpRequest.BodyPublishers.ofString(bodyJson == null ? "" : bodyJson));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    /**
     * 简易 JSON 路径提取：按点分割，如 data.token
     */
    private String extractToken(String json, String jsonPath) {
        try {
            if (json == null || jsonPath == null || jsonPath.isEmpty()) {
                return null;
            }
            JsonNode root = this.objectMapper.readTree(json);
            String[] parts = jsonPath.split("\\.");
            JsonNode cur = root;
            for (String p : parts) {
                if (cur == null) {
                    return null;
                }
                cur = cur.get(p);
            }
            return cur != null && !cur.isMissingNode() && cur.isValueNode() ? cur.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
