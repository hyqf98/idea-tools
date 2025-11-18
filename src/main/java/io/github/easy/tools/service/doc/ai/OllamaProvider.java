package io.github.easy.tools.service.doc.ai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import io.github.easy.tools.ui.config.DocConfigService;

/**
 * Ollama服务提供商实现
 * <p>
 * 实现与Ollama服务的接口
 * </p>
 */
public class OllamaProvider implements AIProvider {
    
    private final DocConfigService config;
    
    public OllamaProvider() {
        this.config = DocConfigService.getInstance();
    }
    
    @Override
    public String sendRequest(AIRequest request) {
        try {
            // 构建请求体
            String requestBody = this.buildRequestBody(request);
            
            // 发送HTTP请求到Ollama API
            HttpResponse response = HttpRequest.post(config.baseUrl + "/api/generate")
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .timeout(config.timeout)
                    .execute();
            
            if (response.getStatus() == 200) {
                return response.body();
            } else {
                throw new RuntimeException("Ollama服务调用失败: " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("调用Ollama服务失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "Ollama";
    }
    
    /**
     * 构建请求体
     *
     * @param request AI请求对象
     * @return 请求体JSON字符串
     */
    private String buildRequestBody(AIRequest request) {
        // 构建Ollama请求体
        return JSONUtil.createObj()
                .set("model", request.getModel())
                .set("prompt", request.getPrompt())
                .set("stream", request.isStream())
                .set("options", JSONUtil.createObj()
                        .set("temperature", request.getTemperature())
                        .set("top_p", request.getTopP())
                        .set("top_k", request.getTopK())
                        .set("num_predict", request.getMaxTokens()))
                .toString();
    }
}