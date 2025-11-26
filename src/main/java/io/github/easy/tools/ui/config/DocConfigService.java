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
 * 文档配置服务类
 * <p>
 * 该类负责管理文档生成相关的配置信息，包括模板、自定义参数等。
 * 使用IntelliJ平台的持久化机制来保存和加载配置。
 * </p>
 */
@State(name = "EasyDocConfig", storages = @Storage("easy-doc-config.xml"))
public final class DocConfigService implements PersistentStateComponent<DocConfigService> {

    /** 作者参数名 */
    public static final String PARAM_AUTHOR = "author";
    /** 日期参数名 */
    public static final String PARAM_DATE = "date";
    /** 版本参数名 */
    public static final String PARAM_VERSION = "version";
    /** 描述参数名 */
    public static final String PARAM_DESCRIPTION = "description";
    /** 起始版本参数名 */
    public static final String PARAM_SINCE = "since";
    /** 字符串工具类参数名 */
    public static final String PARAM_STR = "str";
    /** 参数列表参数名 */
    public static final String PARAM_PARAMETERS = "parameters";
    /** 返回值类型参数名 */
    public static final String PARAM_RETURN_TYPE = "returnType";
    /** 异常列表参数名 */
    public static final String PARAM_EXCEPTIONS = "exceptions";
    /** 字段名称参数名 */
    public static final String PARAM_FIELD_NAME = "fieldName";
    /** 字段类型参数名 */
    public static final String PARAM_FIELD_TYPE = "fieldType";
    /** 参数名称key */
    public static final String PARAM_PARAMETER_NAME = "parameterName";



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

    /** 类注释模板默认值 */
    public static final String DEFAULT_CLASS_TEMPLATE = """
            /**
             * ${description}
             *
             #foreach( $param in $parameters )
             * @param $param.name $param.parameterName
             #end
             * @author ${author}
             * @date ${date}
             * @version ${version}
             * @since ${since}
             */
            """;

    /** 方法注释模板默认值 */
    public static final String DEFAULT_METHOD_TEMPLATE = """
            /**
             * ${description}
             *
             #foreach( $param in $parameters )
             * @param $param.name $param.parameterName
             #end
             #if( $returnType && $returnType != "" )
             * @return $returnType
             #end
             #foreach( $exception in $exceptions )
             * @throws $exception
             #end
             */
            """;

    /** 字段注释模板默认值 */
    public static final String DEFAULT_FIELD_TEMPLATE = """
            /**
             * ${fieldName}
             */
            """;

    /**
     * 类注释模板
     */
    public String classTemplate = DEFAULT_CLASS_TEMPLATE;

    /**
     * 方法注释模板
     */
    public String methodTemplate = DEFAULT_METHOD_TEMPLATE;

    /**
     * 字段注释模板
     */
    public String fieldTemplate = DEFAULT_FIELD_TEMPLATE;

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
        TemplateParameter<String> author1 = new TemplateParameter<>();
        author1.setName(PARAM_AUTHOR);
        author1.setValue(author);
        author1.setDescription("作者");

        TemplateParameter<String> date = new TemplateParameter<>();
        date.setName(PARAM_DATE);
        date.setValue(DateUtil.now());
        date.setDescription("日期");

        TemplateParameter<String> version = new TemplateParameter<>();
        version.setName(PARAM_VERSION);
        version.setValue("1.0.0");
        version.setDescription("版本");

        TemplateParameter<String> str = new TemplateParameter<>();
        str.setName(PARAM_STR);
        str.setValue(StrUtil.class.getName()); // 改为使用类名字符串而不是Class对象
        str.setDescription("字符串工具类");

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
        parameters.put(PARAM_PARAMETERS, "泛型类型参数列表（包含name和parameterName，name格式为<T>）");
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
        parameters.put(PARAM_PARAMETERS, "方法参数列表（包含name和parameterName，泛型参数name格式为<T>）");
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
        parameters.put(PARAM_FIELD_TYPE, "字段类型");
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
