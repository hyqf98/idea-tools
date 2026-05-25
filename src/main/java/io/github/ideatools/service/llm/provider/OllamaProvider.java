package io.github.ideatools.service.llm.provider;

import com.google.gson.JsonObject;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import io.github.ideatools.constants.LLMConstants;
import io.github.ideatools.service.llm.AIRequest;
import io.github.ideatools.ui.config.LLMConfigState;

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

            // 获取有效的baseUrl（优先使用用户配置，否则使用默认值）
            String effectiveBaseUrl = config.getEffectiveBaseUrl(this.configState.defaultModelType);

            // 构建请求体
            String requestBody = this.buildRequestBody(request);

            // 发送HTTP请求到Ollama API
            return HttpRequests.post(effectiveBaseUrl + LLMConstants.ApiEndpoint.OLLAMA_GENERATE, "application/json")
                    .connect(httpRequest -> {
                        httpRequest.write(requestBody);
                        return httpRequest.readString();
                    });
        } catch (IOException e) {
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
        JsonObject json = new JsonObject();
        json.addProperty("model", request.getModel());
        json.addProperty("prompt", request.getPrompt());
        json.addProperty("stream", request.isStream());
        
        JsonObject options = new JsonObject();
        options.addProperty("temperature", request.getTemperature());
        options.addProperty("top_p", request.getTopP());
        options.addProperty("top_k", request.getTopK());
        options.addProperty("num_predict", request.getMaxTokens());
        json.add("options", options);
        
        return json.toString();
    }
}
