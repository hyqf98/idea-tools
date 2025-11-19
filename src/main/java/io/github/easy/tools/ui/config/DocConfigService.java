package io.github.easy.tools.ui.config;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.github.easy.tools.entity.doc.TemplateParameter;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 持久化配置服务类，用于管理插件的各种配置参数
 * <p>
 * 该类负责存储和管理插件的配置信息，包括模板配置和自定义参数等。
 * 通过IntelliJ Platform的持久化机制，配置信息会在IDE重启后保持不变。
 * </p>
 *
 * iamxiaohaijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.09.18 17:22
 * @since y.y.y
 */
@State(
        name = "EasyToolsConfig",
        storages = @Storage("easy-tools-config.xml")
)
public class DocConfigService implements PersistentStateComponent<DocConfigService> {

    /** PARAM_AUTHOR */
    public static final String PARAM_AUTHOR = "author";
    /** PARAM_DATE */
    public static final String PARAM_DATE = "date";
    /** PARAM_VERSION */
    public static final String PARAM_VERSION = "version";
    /** PARAM_STR */
    public static final String PARAM_STR = "str";

    /** PARAM_DESCRIPTION */
    public static final String PARAM_DESCRIPTION = "description";

    /** PARAM_SINCE */
    public static final String PARAM_SINCE = "since";

    /** PARAM_PARAMETERS */
    public static final String PARAM_PARAMETERS = "parameters";

    /** PARAM_RETURN_TYPE */
    public static final String PARAM_RETURN_TYPE = "returnType";

    /** PARAM_EXCEPTIONS */
    public static final String PARAM_EXCEPTIONS = "exceptions";

    /** PARAM_FIELD_NAME */
    public static final String PARAM_FIELD_NAME = "fieldName";

    /**
     * 是否启用AI功能
     */
    public boolean enableAi = false;

    /**
     * AI模型的基础URL
     */
    public String baseUrl = "";

    /**
     * AI模型名称
     */
    public String modelName = "";

    /**
     * AI模型类型
     */
    public String modelType = "openai";

    /**
     * AI模型API密钥
     */
    public String apiKey = "";

    /**
     * 请求超时时间（毫秒）
     */
    public int timeout = 300000;

    /**
     * 温度参数，控制生成文本的随机性
     */
    public double temperature = 0.7;

    /**
     * Top-p参数，控制生成文本的多样性
     */
    public double topP = 1.0;

    /**
     * Top-k参数，控制生成文本的多样性
     */
    public int topK = 40;

    /**
     * 是否开启思考模式
     */
    public boolean enableReasoning = false;

    /**
     * 最大生成令牌数
     */
    public int maxTokens = 2048;

    /**
     * 类注释模板
     */
    public String classTemplate = """
            /**
             * ${description}
             *
             * @author ${author}
             * @date ${date}
             * @version ${version}
             * @since ${since}
             */
            """;

    /**
     * 方法注释模板
     */
    public String methodTemplate = """
            /**
             * ${description}
             *
             #foreach( $param in $parameters )
             * @param $param.name $param.description
             #end
             #if( $returnType )
             * @return $returnType
             #end
             #foreach( $exception in $exceptions )
             * @throws $exception
             #end
             * @author ${author}
             * @date ${date}
             * @version ${version}
             */
            """;

    /**
     * 字段注释模板
     */
    public String fieldTemplate = """
            /**
             * The ${fieldName}.
             */
            """;

    /**
     * 自定义变量字符串形式
     */
    public String customVar = "";

    /**
     * 自定义参数列表
     */
    public List<TemplateParameter> customParameters = new LinkedList<>();

    /**
     * 是否启用保存监听器
     */
    public boolean saveListener = false;

    /**
     * 获取基础参数列表
     * <p>
     * 基础参数包括作者名、当前日期和空描述，这些参数会在所有模板中使用。
     * </p>
     *
     * @return 基础参数列表 base parameters
     * @since y.y.y
     */
    public List<TemplateParameter> getBaseParameters() {
        String author = System.getProperty("user.name");
        List<TemplateParameter> list = new LinkedList<>();
        TemplateParameter<String> author1 = new TemplateParameter<>(PARAM_AUTHOR, author, "作者");
        TemplateParameter<String> date = new TemplateParameter<>(PARAM_DATE, DateUtil.now(), "日期");
        TemplateParameter<String> version = new TemplateParameter<>(PARAM_VERSION, "1.0.0", "版本");
        TemplateParameter<Class<StrUtil>> str = new TemplateParameter<>(PARAM_STR, StrUtil.class, "字符串工具类");

        list.add(author1);
        list.add(date);
        list.add(version);
        list.add(str);
        return list;
    }

    /**
     * 获取类模板可用的内置参数说明
     *
     * @return 类模板参数说明映射 class template parameters
     * @since y.y.y
     */
    public Map<String, String> getClassTemplateParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PARAM_AUTHOR, "作者名称");
        parameters.put(PARAM_DATE, "当前日期");
        parameters.put(PARAM_VERSION, "版本号");
        parameters.put(PARAM_DESCRIPTION, "类描述（默认为类名）");
        parameters.put(PARAM_SINCE, "起始版本");
        parameters.put(PARAM_STR, "Hutool字符串工具类");
        return parameters;
    }

    /**
     * 获取方法模板可用的内置参数说明
     *
     * @return 方法模板参数说明映射 method template parameters
     * @since y.y.y
     */
    public Map<String, String> getMethodTemplateParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PARAM_AUTHOR, "作者名称");
        parameters.put(PARAM_DATE, "当前日期");
        parameters.put(PARAM_VERSION, "版本号");
        parameters.put(PARAM_DESCRIPTION, "方法描述（默认为方法名+method）");
        parameters.put(PARAM_PARAMETERS, "方法参数列表（包含name和description）");
        parameters.put(PARAM_RETURN_TYPE, "返回值类型");
        parameters.put(PARAM_EXCEPTIONS, "抛出的异常列表");
        parameters.put(PARAM_STR, "Hutool字符串工具类");
        return parameters;
    }

    /**
     * 获取字段模板可用的内置参数说明
     *
     * @return 字段模板参数说明映射 field template parameters
     * @since y.y.y
     */
    public Map<String, String> getFieldTemplateParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PARAM_AUTHOR, "作者名称");
        parameters.put(PARAM_DATE, "当前日期");
        parameters.put(PARAM_VERSION, "版本号");
        parameters.put(PARAM_FIELD_NAME, "字段名称");
        parameters.put(PARAM_STR, "Hutool字符串工具类");
        return parameters;
    }

    /**
     * 获取所有内置参数说明
     *
     * @return 所有模板参数说明映射 all template parameters
     * @since y.y.y
     */
    public Map<String, String> getAllTemplateParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();

        // 添加类模板参数
        parameters.putAll(this.getClassTemplateParameters());

        // 添加方法模板参数
        parameters.putAll(this.getMethodTemplateParameters());

        // 添加字段模板参数
        parameters.putAll(this.getFieldTemplateParameters());

        return parameters;
    }

    /**
     * 获取配置服务的单例实例
     *
     * @return DocConfigService的单例实例 instance
     * @since y.y.y
     */
    public static DocConfigService getInstance() {
        return ApplicationManager.getApplication().getService(DocConfigService.class);
    }

    /**
     * 获取当前状态（用于持久化）
     *
     * @return 当前配置服务实例 state
     * @since y.y.y
     */
    @Override
    public DocConfigService getState() {
        return this;
    }

    /**
     * 加载状态（用于持久化）
     *
     * @param state 要加载的配置状态
     * @since y.y.y
     */
    @Override
    public void loadState(@NotNull DocConfigService state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
