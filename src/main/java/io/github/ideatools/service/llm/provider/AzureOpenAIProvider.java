package io.github.ideatools.service.llm.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import io.github.ideatools.constants.LLMConstants;
import io.github.ideatools.service.llm.AIRequest;
import io.github.ideatools.ui.config.LLMConfigState;

/**
 * <p> Azure OpenAI服务提供商实现 </p>
 * <p>
 * Azure OpenAI使用独立的认证方式（api-key header）
 * API文档: https://learn.microsoft.com/azure/ai-services/openai/
 * </p>
 * <p>
 * 注意：Azure OpenAI的baseUrl格式为:
 * https://{your-resource-name}.openai.azure.com/openai/deployments/{deployment-id}
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class AzureOpenAIProvider implements AIProvider {

    /**
     * LLM配置状态服务实例
     */
    private final LLMConfigState configState;

    /**
     * 构造函数，初始化配置服务
     */
    public AzureOpenAIProvider() {
        this.configState = LLMConfigState.getInstance();
    }

    /**
     * 发送请求到Azure OpenAI服务并获取响应
     *
     * @param request AI请求对象
     * @return AI响应内容
     * @since 1.0.0
     */
    @Override
    public String sendRequest(AIRequest request) {
        try {
            // 获取默认模型配置
            LLMConfigState.ModelConfig config = this.configState.getDefaultModelConfig();

            // 获取有效的baseUrl（Azure必须用户配置，否则会报错）
            String effectiveBaseUrl = config.getEffectiveBaseUrl(this.configState.defaultModelType);

            if (effectiveBaseUrl == null || effectiveBaseUrl.trim().isEmpty()) {
                throw new RuntimeException("Azure OpenAI需要配置baseUrl，格式为: https://{resource-name}.openai.azure.com/openai/deployments/{deployment-id}");
            }

            // 构建请求体
            String requestBody = this.buildRequestBody(request);

            // Azure需要在URL中添加api-version参数
            String url = effectiveBaseUrl + LLMConstants.ApiEndpoint.OPENAI_CHAT + "?api-version=2024-02-15-preview";

            // 发送HTTP请求，Azure使用api-key header认证
            return HttpRequests.post(url, "application/json")
                    .tuner(connection -> {
                        connection.setRequestProperty("api-key", config.apiKey);
                    })
                    .connect(httpRequest -> {
                        httpRequest.write(requestBody);
                        return httpRequest.readString();
                    });
        } catch (IOException e) {
            throw new RuntimeException("调用Azure OpenAI服务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取提供商名称
     *
     * @return 提供商名称
     * @since 1.0.0
     */
    @Override
    public String getProviderName() {
        return LLMConstants.ModelDisplayName.AZURE;
    }

    /**
     * 构建Azure OpenAI API请求体
     * <p>
     * 构建符合Azure OpenAI API规范的JSON请求体
     * 如果部署的是推理模型（如o1, o3），支持reasoning_effort参数
     * </p>
     *
     * @param request AI请求对象
     * @return 请求体JSON字符串
     * @since 1.0.0
     */
    private String buildRequestBody(AIRequest request) {
        // 构建消息数组
        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", LLMConstants.SystemPrompt.JAVA_DOC_SYSTEM);
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", request.getPrompt());
        messages.add(userMsg);

        // 构建请求体
        JsonObject json = new JsonObject();
        // Azure OpenAI不需要指定model，因为模型在deployment中已确定
        json.add("messages", messages);
        json.addProperty("temperature", request.getTemperature());
        json.addProperty("top_p", request.getTopP());
        json.addProperty("max_tokens", request.getMaxTokens());
        json.addProperty("stream", request.isStream());

        // Azure OpenAI推理模型支持reasoning_effort参数（如果部署的是推理模型）
        if (request.isEnableReasoning()) {
            json.addProperty("reasoning_effort", "medium");
        }

        return json.toString();
    }
}
