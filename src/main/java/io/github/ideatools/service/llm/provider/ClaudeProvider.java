package io.github.ideatools.service.llm.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import io.github.ideatools.constants.LLMConstants;
import io.github.ideatools.service.llm.AIRequest;
import io.github.ideatools.ui.config.LLMConfigState;

/**
 * <p> Anthropic Claude服务提供商实现 </p>
 * <p>
 * Claude使用Anthropic原生的Messages API
 * API文档: https://docs.anthropic.com/claude/reference/messages_post
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class ClaudeProvider implements AIProvider {

    /**
     * Claude API版本
     */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * Claude Messages API端点
     */
    private static final String MESSAGES_ENDPOINT = "/messages";

    /**
     * LLM配置状态服务实例
     */
    private final LLMConfigState configState;

    /**
     * 构造函数，初始化配置服务
     */
    public ClaudeProvider() {
        this.configState = LLMConfigState.getInstance();
    }

    /**
     * 发送请求到Claude服务并获取响应
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

            // 发送HTTP请求，Claude使用x-api-key header认证
            return HttpRequests.post(effectiveBaseUrl + MESSAGES_ENDPOINT, "application/json")
                    .tuner(connection -> {
                        connection.setRequestProperty("x-api-key", config.apiKey);
                        connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
                        connection.setRequestProperty("content-type", "application/json");
                    })
                    .connect(httpRequest -> {
                        httpRequest.write(requestBody);
                        return httpRequest.readString();
                    });
        } catch (IOException e) {
            throw new RuntimeException("调用Claude服务失败: " + e.getMessage(), e);
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
        return LLMConstants.ModelDisplayName.CLAUDE;
    }

    /**
     * 构建Claude API请求体
     * <p>
     * 构建符合Anthropic Messages API规范的JSON请求体
     * Claude支持扩展思考模式(Extended Thinking)，通过thinking参数控制
     * </p>
     *
     * @param request AI请求对象
     * @return 请求体JSON字符串
     * @since 1.0.0
     */
    private String buildRequestBody(AIRequest request) {
        // 构建消息数组
        JsonArray messages = new JsonArray();

        // Claude API中，user消息是必须的
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", request.getPrompt());
        messages.add(userMsg);

        // 构建请求体
        JsonObject json = new JsonObject();
        json.addProperty("model", request.getModel());
        json.add("messages", messages);
        json.addProperty("system", LLMConstants.SystemPrompt.JAVA_DOC_SYSTEM);
        json.addProperty("max_tokens", request.getMaxTokens());
        json.addProperty("temperature", request.getTemperature());
        json.addProperty("top_p", request.getTopP());
        json.addProperty("stream", request.isStream());

        // Claude扩展思考模式控制: thinking: { type: "enabled", budget_tokens: N }
        // 只有开启思考模式时才添加thinking参数
        if (request.isEnableReasoning()) {
            JsonObject thinking = new JsonObject();
            thinking.addProperty("type", "enabled");
            // budget_tokens必须小于max_tokens，默认设置为max_tokens的1/4，最少1024
            int budgetTokens = Math.max(1024, request.getMaxTokens() / 4);
            thinking.addProperty("budget_tokens", budgetTokens);
            json.add("thinking", thinking);
        }

        return json.toString();
    }
}
