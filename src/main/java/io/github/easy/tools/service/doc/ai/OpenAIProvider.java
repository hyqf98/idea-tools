package io.github.easy.tools.service.doc.ai;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import io.github.easy.tools.ui.config.DocConfigService;

/**
 * OpenAI服务提供商实现
 * <p>
 * 实现与OpenAI兼容的AI服务接口
 * </p>
 */
public class OpenAIProvider implements AIProvider {
    
    private final DocConfigService config;
    
    public OpenAIProvider() {
        this.config = DocConfigService.getInstance();
    }
    
    @Override
    public String sendRequest(AIRequest request) {
        try {
            // 构建请求体
            String requestBody = this.buildRequestBody(request);
            
            // 发送HTTP请求
            HttpResponse response = HttpRequest.post(config.baseUrl + "/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey)
                    .body(requestBody)
                    .timeout(config.timeout)
                    .execute();
            
            if (response.getStatus() == 200) {
                return response.body();
            } else {
                throw new RuntimeException("AI服务调用失败: " + response.body());
            }
        } catch (Exception e) {
            throw new RuntimeException("调用OpenAI服务失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String getProviderName() {
        return "OpenAI";
    }
    
    /**
     * 构建请求体
     *
     * @param request AI请求对象
     * @return 请求体JSON字符串
     */
    private String buildRequestBody(AIRequest request) {
        // 构建消息数组
        JSON messages = JSONUtil.createArray()
                .put(JSONUtil.createObj()
                        .set("role", "system")
                        .set("content", "你是一名专业的Java开发者，擅长编写高质量的Java代码注释。请根据提供的代码和上下文信息生成符合JavaDoc标准的注释。"))
                .put(JSONUtil.createObj()
                        .set("role", "user")
                        .set("content", request.getPrompt()));
        
        // 构建请求体
        return JSONUtil.createObj()
                .set("model", request.getModel())
                .set("messages", messages)
                .set("temperature", request.getTemperature())
                .set("top_p", request.getTopP())
                .set("max_tokens", request.getMaxTokens())
                .set("stream", request.isStream())
                .toString();
    }
}