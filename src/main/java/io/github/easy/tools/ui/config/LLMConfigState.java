package io.github.easy.tools.ui.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.github.easy.tools.constants.LLMConstants;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM配置状态服务类
 * <p>
 * 负责管理不同模型类型的LLM配置信息，每个模型类型独立保存配置。
 * 使用IntelliJ平台的持久化机制来保存和加载配置。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
@Service(Service.Level.APP)
@State(name = "LLMConfigState", storages = @Storage("llm-config-state.xml"))
public final class LLMConfigState implements PersistentStateComponent<LLMConfigState> {

    /**
     * 默认使用的模型类型
     */
    public String defaultModelType = LLMConstants.ModelType.OPENAI;

    /**
     * 各模型类型的配置映射
     * key: 模型类型 (openai, ollama, azure等)
     * value: 该模型类型的配置信息
     */
    public Map<String, ModelConfig> modelConfigs = new HashMap<>();

    /**
     * 单个模型的配置信息
     */
    @Data
    public static class ModelConfig {
        /**
         * 模型基础URL
         */
        public String baseUrl = "";

        /**
         * 模型名称
         */
        public String modelName = "";

        /**
         * API密钥
         */
        public String apiKey = "";

        /**
         * 请求超时时间（毫秒）
         */
        public int timeout = LLMConstants.DefaultConfig.TIMEOUT;

        /**
         * 温度参数，控制生成文本的随机性 (0.0-2.0)
         */
        public double temperature = LLMConstants.DefaultConfig.TEMPERATURE;

        /**
         * Top-p参数，控制生成文本的多样性 (0.0-1.0)
         */
        public double topP = LLMConstants.DefaultConfig.TOP_P;

        /**
         * Top-k参数，控制生成文本的多样性
         */
        public int topK = LLMConstants.DefaultConfig.TOP_K;

        /**
         * 最大生成令牌数
         */
        public int maxTokens = LLMConstants.DefaultConfig.MAX_TOKENS;

        /**
         * 是否开启思考模式
         */
        public boolean enableReasoning = false;
    }

    /**
     * 获取配置服务的单例实例
     *
     * @return LLMConfigState的单例实例
     * @since 1.0.0
     */
    public static LLMConfigState getInstance() {
        return ApplicationManager.getApplication().getService(LLMConfigState.class);
    }

    /**
     * 获取指定模型类型的配置
     * <p>
     * 如果该模型类型尚未配置，则创建默认配置
     * </p>
     *
     * @param modelType 模型类型
     * @return 模型配置
     * @since 1.0.0
     */
    public ModelConfig getModelConfig(String modelType) {
        return this.modelConfigs.computeIfAbsent(modelType, k -> new ModelConfig());
    }

    /**
     * 保存指定模型类型的配置
     *
     * @param modelType 模型类型
     * @param config    模型配置
     * @since 1.0.0
     */
    public void setModelConfig(String modelType, ModelConfig config) {
        this.modelConfigs.put(modelType, config);
    }

    /**
     * 获取默认模型类型的配置
     *
     * @return 默认模型配置
     * @since 1.0.0
     */
    public ModelConfig getDefaultModelConfig() {
        return this.getModelConfig(this.defaultModelType);
    }

    /**
     * 设置默认模型类型
     *
     * @param modelType 模型类型
     * @since 1.0.0
     */
    public void setDefaultModelType(String modelType) {
        this.defaultModelType = modelType;
    }

    /**
     * 获取当前状态（用于持久化）
     *
     * @return 当前配置服务实例
     * @since 1.0.0
     */
    @Override
    public LLMConfigState getState() {
        return this;
    }

    /**
     * 加载状态（用于持久化）
     *
     * @param state 要加载的配置状态
     * @since 1.0.0
     */
    @Override
    public void loadState(@NotNull LLMConfigState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
