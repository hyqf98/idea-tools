package io.github.ideatools.service.llm.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.intellij.util.io.HttpRequests;
import java.io.IOException;
import io.github.ideatools.constants.LLMConstants;
import io.github.ideatools.service.llm.AIRequest;
import io.github.ideatools.ui.config.LLMConfigState;

/**
 * <p> 百度文心一言(Wenxin)服务提供商实现 </p>
 * <p>
 * 文心一言使用百度千帆平台的API
 * API文档: https://cloud.baidu.com/doc/WENXINWORKSHOP/index.html
 * </p>
 * <p>
 * 注意：文心一言的baseUrl格式为:
 * https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop
 * 需要将API Key作为access_token参数传递
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 10:00
 * @since 1.0.0
 */
public class WenxinProvider implements AIProvider {

    /**
     * 文心一言聊天API端点
     */
    private static final String CHAT_ENDPOINT = "/chat/";

    /**
     * LLM配置状态服务实例
     */
    private final LLMConfigState configState;

    /**
     * 构造函数，初始化配置服务
     */
    public WenxinProvider() {
        this.configState = LLMConfigState.getInstance();
    }

    /**
     * 发送请求到文心一言服务并获取响应
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

            // 文心一言使用access_token参数传递API Key
            String url = effectiveBaseUrl + CHAT_ENDPOINT + request.getModel() + "?access_token=" + config.apiKey;

            // 发送HTTP请求
            return HttpRequests.post(url, "application/json")
                    .connect(httpRequest -> {
                        httpRequest.write(requestBody);
                        return httpRequest.readString();
                    });
        } catch (IOException e) {
            throw new RuntimeException("调用文心一言服务失败: " + e.getMessage(), e);
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
        return LLMConstants.ModelDisplayName.WENXIN;
    }

    /**
     * 构建文心一言API请求体
     * <p>
     * 构建符合百度文心一言API规范的JSON请求体
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
        json.add("messages", messages);
        json.addProperty("temperature", request.getTemperature());
        json.addProperty("top_p", request.getTopP());
        json.addProperty("max_output_tokens", request.getMaxTokens());
        json.addProperty("stream", request.isStream());

        return json.toString();
    }
}
