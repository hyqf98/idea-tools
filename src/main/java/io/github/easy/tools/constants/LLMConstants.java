package io.github.easy.tools.constants;

/**
 * <p> LLM相关常量类 </p>
 * <p>
 * 统一管理大语言模型相关的常量，包括模型类型、提供商名称、默认配置等
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.28 09:00
 * @since 1.0.0
 */
public final class LLMConstants {

    /**
     * 私有构造函数，防止实例化
     */
    private LLMConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 模型类型常量
     */
    public static final class ModelType {
        /** OpenAI模型类型 */
        public static final String OPENAI = "openai";
        /** Ollama模型类型 */
        public static final String OLLAMA = "ollama";
        /** Azure OpenAI模型类型 */
        public static final String AZURE = "azure";
        /** Google Gemini模型类型 */
        public static final String GEMINI = "gemini";
        /** Anthropic Claude模型类型 */
        public static final String CLAUDE = "claude";
        /** DeepSeek模型类型 */
        public static final String DEEPSEEK = "deepseek";
        /** Qwen(通义千问)模型类型 */
        public static final String QWEN = "qwen";
        /** GLM(智谱清言)模型类型 */
        public static final String GLM = "glm";
        /** Baidu Wenxin(文心一言)模型类型 */
        public static final String WENXIN = "wenxin";

        private ModelType() {
        }
    }

    /**
     * 模型显示名称常量
     */
    public static final class ModelDisplayName {
        /** OpenAI显示名称 */
        public static final String OPENAI = "OpenAI";
        /** Ollama显示名称 */
        public static final String OLLAMA = "Ollama";
        /** Azure OpenAI显示名称 */
        public static final String AZURE = "Azure OpenAI";
        /** Google Gemini显示名称 */
        public static final String GEMINI = "Google Gemini";
        /** Anthropic Claude显示名称 */
        public static final String CLAUDE = "Anthropic Claude";
        /** DeepSeek显示名称 */
        public static final String DEEPSEEK = "DeepSeek";
        /** Qwen(通义千问)显示名称 */
        public static final String QWEN = "Qwen (通义千问)";
        /** GLM(智谱清言)显示名称 */
        public static final String GLM = "GLM (智谱清言)";
        /** Baidu Wenxin(文心一言)显示名称 */
        public static final String WENXIN = "Baidu Wenxin (文心一言)";

        private ModelDisplayName() {
        }
    }

    /**
     * API端点常量
     */
    public static final class ApiEndpoint {
        /** OpenAI聊天补全端点 */
        public static final String OPENAI_CHAT = "/chat/completions";
        /** OpenAI补全端点 */
        public static final String OPENAI_COMPLETION = "/completions";
        /** Ollama生成端点 */
        public static final String OLLAMA_GENERATE = "/api/generate";
        /** Ollama聊天端点 */
        public static final String OLLAMA_CHAT = "/api/chat";

        private ApiEndpoint() {
        }
    }

    /**
     * 默认配置常量
     * <p>
     * 参数说明：
     * - Temperature: 控制随机性，0.7是平衡创造性和确定性的推荐值
     * - Top-p: 核采样参数，0.9是推荐值，保留概率质量前90%的token
     * - Top-k: 限制采样的token数量，50是通用推荐值（注意：top-k是整数，不是小数）
     * - Max Tokens: 最大生成令牌数，4096可以处理大多数场景
     * </p>
     */
    public static final class DefaultConfig {
        /** 默认超时时间（毫秒） */
        public static final int TIMEOUT = 300000;
        /** 默认温度参数 (0.0-2.0，推荐0.7) */
        public static final double TEMPERATURE = 0.7;
        /** 默认Top-p参数 (0.0-1.0，推荐0.9) */
        public static final double TOP_P = 0.9;
        /** 默认Top-k参数 (整数，推荐50) */
        public static final int TOP_K = 50;
        /** 默认最大令牌数 (推荐4096) */
        public static final int MAX_TOKENS = 12800;
        /** 默认思考模式开关 */
        public static final boolean ENABLE_REASONING = false;

        private DefaultConfig() {
        }
    }

    /**
     * 系统提示词常量
     */
    public static final class SystemPrompt {
        /** Java代码注释生成系统提示词 */
        public static final String JAVA_DOC_SYSTEM = "你是一名专业的Java开发者，擅长编写高质量的Java代码注释。请根据提供的代码和上下文信息生成符合JavaDoc标准的注释。";

        private SystemPrompt() {
        }
    }
}
