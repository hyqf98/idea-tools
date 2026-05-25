package io.github.ideatools.service.llm.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import io.github.ideatools.constants.LLMConstants;
import io.github.ideatools.service.llm.AIRequest;
import io.github.ideatools.ui.config.LLMConfigState;

/**
 * <p> Google Gemini服务提供商实现 </p>
 * <p>
 * Gemini使用Google原生的GenerateContent API
 * API文档: https://ai.google.dev/tutorials/rest_quickstart
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class GeminiProvider implements AIProvider {

    /**
     * Gemini GenerateContent API端点模板
     */
    private static final String GENERATE_CONTENT_ENDPOINT = "/models/{model}:generateContent";

    /**
     * LLM配置状态服务实例
     */
    private final LLMConfigState configState;

    /**
     * 构造函数，初始化配置服务
     */
    public GeminiProvider() {
        this.configState = LLMConfigState.getInstance();
    }

    /**
     * 发送请求到Gemini服务并获取响应
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

            // 构建完整的API URL，Gemini使用URL参数传递API Key
            String endpoint = GENERATE_CONTENT_ENDPOINT.replace("{model}", request.getModel());
            String url = effectiveBaseUrl + endpoint + "?key=" + config.apiKey;

            // 发送HTTP请求
            return HttpRequests.post(url, "application/json")
                    .connect(httpRequest -> {
                        httpRequest.write(requestBody);
                        return httpRequest.readString();
                    });
        } catch (IOException e) {
            throw new RuntimeException("调用Gemini服务失败: " + e.getMessage(), e);
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
        return LLMConstants.ModelDisplayName.GEMINI;
    }

    /**
     * 构建Gemini API请求体
     * <p>
     * 构建符合Google Gemini GenerateContent API规范的JSON请求体
     * Gemini 2.5系列支持思考模式，通过thinkingBudget参数控制
     * - thinkingBudget = 0: 关闭思考模式
     * - thinkingBudget = -1: 自动控制（默认）
     * - thinkingBudget > 0: 手动设置token预算
     * </p>
     *
     * @param request AI请求对象
     * @return 请求体JSON字符串
     * @since 1.0.0
     */
    private String buildRequestBody(AIRequest request) {
        // 构建contents数组
        JsonArray contents = new JsonArray();

        // 构建用户消息
        JsonObject content = new JsonObject();
        JsonArray parts = new JsonArray();

        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", request.getPrompt());
        parts.add(textPart);

        content.add("parts", parts);
        contents.add(content);

        // 构建generationConfig
        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", request.getTemperature());
        generationConfig.addProperty("topP", request.getTopP());
        generationConfig.addProperty("topK", request.getTopK());
        generationConfig.addProperty("maxOutputTokens", request.getMaxTokens());

        // Gemini思考模式控制: thinkingBudget
        // 0 = 关闭思考模式, -1 = 自动控制, >0 = 手动设置token预算
        // 这里默认设置为-1（自动），如果enableReasoning为false则设置为0关闭
        generationConfig.addProperty("thinkingBudget", request.isEnableReasoning() ? -1 : 0);

        // 构建systemInstruction
        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemTextPart = new JsonObject();
        systemTextPart.addProperty("text", LLMConstants.SystemPrompt.JAVA_DOC_SYSTEM);
        systemParts.add(systemTextPart);
        systemInstruction.add("parts", systemParts);

        // 构建请求体
        JsonObject json = new JsonObject();
        json.add("contents", contents);
        json.add("generationConfig", generationConfig);
        json.add("systemInstruction", systemInstruction);

        return json.toString();
    }
}
