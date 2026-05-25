package io.github.ideatools.service.llm.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import io.github.ideatools.constants.LLMConstants;
import io.github.ideatools.service.llm.AIRequest;
import io.github.ideatools.ui.config.LLMConfigState;

/**
 * <p> 通义千问(Qwen)服务提供商实现 </p>
 * <p>
 * 通义千问使用阿里云DashScope的OpenAI兼容模式API
 * API文档: https://help.aliyun.com/zh/model-studio/developer-reference/use-qwen-by-calling-api
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class QwenProvider implements AIProvider {

    /**
     * LLM配置状态服务实例
     */
    private final LLMConfigState configState;

    /**
     * 构造函数，初始化配置服务
     */
    public QwenProvider() {
        this.configState = LLMConfigState.getInstance();
    }

    /**
     * 发送请求到通义千问服务并获取响应
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

            // 获取有效的baseUrl（优先使用用户配置，否则使用默认值）
            String effectiveBaseUrl = config.getEffectiveBaseUrl(this.configState.defaultModelType);

            // 构建请求体
            String requestBody = this.buildRequestBody(request);

            // 发送HTTP请求
            return HttpRequests.post(effectiveBaseUrl + LLMConstants.ApiEndpoint.OPENAI_CHAT, "application/json")
                    .tuner(connection -> {
                        connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
                    })
                    .connect(httpRequest -> {
                        httpRequest.write(requestBody);
                        return httpRequest.readString();
                    });
        } catch (IOException e) {
            throw new RuntimeException("调用通义千问服务失败: " + e.getMessage(), e);
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
        return LLMConstants.ModelDisplayName.QWEN;
    }

    /**
     * 构建通义千问API请求体
     * <p>
     * 构建符合通义千问OpenAI兼容模式API规范的JSON请求体
     * 通义千问支持思考模式，通过enable_thinking参数控制
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
        json.addProperty("model", request.getModel());
        json.add("messages", messages);
        json.addProperty("temperature", request.getTemperature());
        json.addProperty("top_p", request.getTopP());
        json.addProperty("max_tokens", request.getMaxTokens());
        json.addProperty("stream", request.isStream());

        // 通义千问思考模式控制: enable_thinking: true | false
        json.addProperty("enable_thinking", request.isEnableReasoning());

        return json.toString();
    }
}
