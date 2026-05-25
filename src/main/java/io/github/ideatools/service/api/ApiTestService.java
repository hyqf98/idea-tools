package io.github.ideatools.service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import io.github.ideatools.entity.api.ApiInfo;
import io.github.ideatools.ui.config.ApiTestConfigState;
import io.github.ideatools.utils.NotificationUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p> API 测试服务 </p>
 * <p>
 * 负责执行API测试请求，自动合并公共请求头，并在需要时执行前置登录请求以缓存Token。
 * </p>
 *
 * @author haijun
 * @date 2025-12-23 09:37:30
 * @version 1.0.0
 * @since 1.0.0
 */
public class ApiTestService {

    /**
     * object mapper
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * http client
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 执行API请求（支持动态请求头解析）
     *
     * @param project 项目
     * @param apiInfo API信息
     * @param interfaceHeaders 接口级请求头列表
     * @param bodyJson 请求体（JSON，可为空）
     * @return 包含statusCode、headers、body的Map
     * @since 1.0.0
     */
    public Map<String, Object> executeWithHeaders(Project project, ApiInfo apiInfo,
                                                   List<ApiTestConfigState.HeaderItem> interfaceHeaders,
                                                   String bodyJson) {
        Map<String, Object> result = new HashMap<>();
        try {
            ApiTestConfigState cfg = ApiTestConfigState.getInstance(project);

            // 检查baseUrl配置
            if (cfg.baseUrl == null || cfg.baseUrl.trim().isEmpty()) {
                String errorMsg = "请先配置服务基础地址(baseUrl)";
                NotificationUtil.showError(project, errorMsg);
                result.put("statusCode", 0);
                result.put("headers", new HashMap<>());
                result.put("body", "错误: " + errorMsg);
                return result;
            }

            Map<String, String> headers = new HashMap<>();

            // 1. 解析全局请求头（支持动态值）
            Map<String, String> globalHeaders = this.resolveHeaders(cfg.commonHeaders, cfg.baseUrl);
            headers.putAll(globalHeaders);

            // 2. 解析接口级请求头（优先级更高，会覆盖全局）
            Map<String, String> localHeaders = this.resolveHeaders(interfaceHeaders, cfg.baseUrl);
            headers.putAll(localHeaders);

            // 3. 前置登录请求
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

            result.put("statusCode", response.statusCode());
            result.put("headers", response.headers().map());
            result.put("body", response.body());
            return result;
        } catch (Exception e) {
            NotificationUtil.showError(project, "API请求失败: " + e.getMessage());
            result.put("statusCode", 0);
            result.put("headers", new HashMap<>());
            result.put("body", "错误: " + e.getMessage());
            return result;
        }
    }

    /**
     * 解析请求头列表（支持FIXED和DYNAMIC两种类型）
     *
     * @param headerItems 请求头配置列表
     * @param baseUrl 基础URL
     * @return 解析后的请求头Map
     * @since 1.0.0
     */
    private Map<String, String> resolveHeaders(List<ApiTestConfigState.HeaderItem> headerItems, String baseUrl) {
        Map<String, String> headers = new HashMap<>();
        if (headerItems == null) {
            return headers;
        }
        for (ApiTestConfigState.HeaderItem item : headerItems) {
            if (item == null || StringUtil.isEmpty(item.getName())) {
                continue;
            }
            String value = null;
            if (item.getValueType() == ApiTestConfigState.ValueType.DYNAMIC) {
                // 动态获取值：先调用来源接口，再通过表达式提取
                value = this.resolveDynamicValue(item, baseUrl);
            } else {
                // 固定值
                value = item.getValue();
            }
            if (value != null) {
                headers.put(item.getName(), value);
            }
        }
        return headers;
    }

    /**
     * 解析动态请求头值：调用来源接口，使用hutool风格的表达式提取值
     *
     * @param item 请求头配置项
     * @param baseUrl 基础URL
     * @return 解析后的值
     * @since 1.0.0
     */
    private String resolveDynamicValue(ApiTestConfigState.HeaderItem item, String baseUrl) {
        try {
            if (StringUtil.isEmpty(item.getSourceUrl())) {
                return null;
            }
            // 调用来源接口
            String url = this.buildAbsoluteUrl(baseUrl, item.getSourceUrl());
            HttpRequest request = this.buildRequest(
                url,
                StringUtil.defaultIfEmpty(item.getSourceMethod(), "GET"),
                null,
                item.getSourceBody()
            );
            HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // 使用表达式提取值（支持${response.data.token}语法）
            String expression = item.getValue();
            if (StringUtil.isEmpty(expression)) {
                return responseBody;
            }
            return this.extractValueByExpression(responseBody, expression);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 使用表达式从响应中提取值
     * 支持语法：${response.data.token} 或直接使用jsonPath如 data.token
     *
     * @param responseBody 响应体JSON
     * @param expression 表达式
     * @return 提取的值
     * @since 1.0.0
     */
    private String extractValueByExpression(String responseBody, String expression) {
        try {
            // 提取${...}中的路径
            String jsonPath = expression;
            if (expression.contains("${") && expression.contains("}")) {
                Pattern pattern = Pattern.compile("\\$\\{(.+?)\\}");
                Matcher matcher = pattern.matcher(expression);
                if (matcher.find()) {
                    jsonPath = matcher.group(1);
                }
            }
            // 去掉response.前缀（如果有）
            if (jsonPath != null && jsonPath.startsWith("response.")) {
                jsonPath = jsonPath.substring("response.".length());
            }
            // 使用JSON路径提取
            return this.extractToken(responseBody, jsonPath);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 执行API请求（自动处理前置登录与公共头）
     *
     * @param project 项目
     * @param apiInfo API信息
     * @param customHeaders 额外临时头（来自面板输入）
     * @param bodyJson 请求体（JSON，可为空）
     * @return 响应文本
     * @since 1.0.0
     */
    public String execute(Project project, ApiInfo apiInfo, Map<String, String> customHeaders, String bodyJson) {
        try {
            ApiTestConfigState cfg = ApiTestConfigState.getInstance(project);

            // 检查baseUrl配置
            if (cfg.baseUrl == null || cfg.baseUrl.trim().isEmpty()) {
                String errorMsg = "请先配置服务基础地址(baseUrl)";
                NotificationUtil.showError(project, errorMsg);
                return "错误: " + errorMsg;
            }

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

    /**
     * Execute Pre Request And Cache
     *
     * @param cfg cfg
     * @param tokenCache token cache
     * @return string
     * @throws Exception
     * @since 1.0.0
     */
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

    /**
     * Add Headers
     *
     * @param target target
     * @param items items
     * @since 1.0.0
     */
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

    /**
     * Build Absolute Url
     *
     * @param baseUrl base url
     * @param path path
     * @return string
     * @since 1.0.0
     */
    private String buildAbsoluteUrl(String baseUrl, String path) {
        // 如果path已经是完整的URL，直接返回
        if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
            return path;
        }

        // 如果baseUrl为空或空字符串
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            // 如果path是绝对路径（以/开头），将其作为相对路径返回
            // 但这通常不是有效的URL，需要用户配置baseUrl
            if (path != null && !path.trim().isEmpty()) {
                return path;
            }
            return "";
        }

        // 正常拼接URL
        String b = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String p = (path != null && path.startsWith("/")) ? path : "/" + (path == null ? "" : path);
        return b + p;
    }

    /**
     * Build Request
     *
     * @param url url
     * @param method method
     * @param headers headers
     * @param bodyJson body json
     * @return http request
     * @since 1.0.0
     */
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
     *
     * @param json json
     * @param jsonPath json path
     * @return string
     * @since 1.0.0
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
