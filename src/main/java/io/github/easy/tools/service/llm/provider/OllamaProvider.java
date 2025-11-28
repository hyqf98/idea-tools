package io.github.easy.tools.service.llm.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import io.github.easy.tools.constants.LLMConstants;
import io.github.easy.tools.service.llm.AIRequest;
import io.github.easy.tools.ui.config.LLMConfigState;

/**
 * <p> Ollama服务提供商实现 </p>
 * <p>
 * 实现与Ollama本地大模型服务的接口对接
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class OllamaProvider implements AIProvider {
    
    /**
     * LLM配置状态服务实例
     */
    private final LLMConfigState configState;
    
    /**
     * 构造函数，初始化配置服务
     */
    public OllamaProvider() {
        this.configState = LLMConfigState.getInstance();
    }
    
    @Override
    public String sendRequest(AIRequest request) {
        try {
            // 获取默认模型配置
            LLMConfigState.ModelConfig config = this.configState.getDefaultModelConfig();
            
            // 构建请求体
            String requestBody = this.buildRequestBody(request);
            
            // 发送HTTP请求到Ollama API
            HttpResponse response = HttpRequest.post(config.baseUrl + LLMConstants.ApiEndpoint.OLLAMA_GENERATE)
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
        return LLMConstants.ModelDisplayName.OLLAMA;
    }
    
    /**
     * 构建Ollama API请求体
     *
     * @param request AI请求对象
     * @return 请求体JSON字符串
     * @since 1.0.0
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
