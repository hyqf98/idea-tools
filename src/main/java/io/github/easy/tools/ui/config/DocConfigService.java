package io.github.easy.tools.ui.config;

import cn.hutool.core.date.DateUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.github.easy.tools.constants.PromptConstants;
import io.github.easy.tools.utils.TemplateUtils;
import org.jetbrains.annotations.NotNull;
import lombok.Data;

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
@Service(Service.Level.APP)
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
    /** 返回值类型简单名称参数名 */
    public static final String PARAM_RETURN_TYPE_SIMPLE = "returnTypeSimple";
    /** 异常列表参数名 */
    public static final String PARAM_EXCEPTIONS = "exceptions";
    /** 字段名称参数名 */
    public static final String PARAM_FIELD_NAME = "fieldName";
    /** 字段类型参数名 */
    public static final String PARAM_FIELD_TYPE = "fieldType";
    /** 邮箱参数名 */
    public static final String PARAM_EMAIL = "email";




    /** 类注释模板默认值 */
    public static final String DEFAULT_CLASS_TEMPLATE = """
            /**
             * ${description}
             *
             #foreach( $param in $parameters )
             * @param $param.originalName $param.splitName
             #end
             * @author ${author}
             #if( $email && $email != "" )
             * @email ${email}
             #end
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
             * @param $param.originalName $param.splitName
             #end
             #if( $returnType && $returnType != "" )
             * @return $returnType.splitName
             #end
             #foreach( $exception in $exceptions )
             * @throws $exception
             #end
             * @since ${since}
             */
            """;

    /** 字段注释模板默认值 */
    public static final String DEFAULT_FIELD_TEMPLATE = """
            /**
             * ${util.camelToWords($fieldName)} field of type ${fieldType}.
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
    public List<CustomParam> customParameters = new LinkedList<>();

    /**
     * 是否启用保存监听器
     */
    public boolean saveListener = false;

    /**
     * 是否添加非标准注释
     */
    public boolean nonStandardDoc = true;

    /**
     * AI类注释生成提示词模板
     */
    public String classPrompt = PromptConstants.DEFAULT_CLASS_PROMPT;

    /**
     * AI方法注释生成提示词模板
     */
    public String methodPrompt = PromptConstants.DEFAULT_METHOD_PROMPT;

    /**
     * AI字段注释生成提示词模板
     */
    public String fieldPrompt = PromptConstants.DEFAULT_FIELD_PROMPT;

    /**
     * 自定义变量实体，用于持久化存储自定义变量的名称、描述和值
     */
    @Data
    public static class CustomParam {
        /** 变量名 */
        private String name;
        /** 变量描述 */
        private String description;
        /** 变量默认值 */
        private String value;
    }

    /**
     * 获取基础参数列表
     * <p>
     * 基础参数包括作者名、当前日期和空描述，这些参数会在所有模板中使用。
     * </p>
     *
     * @return 基础参数列表 base parameters
     * @since y.y.y
     */
    public Map<String, Object> getBaseParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(PARAM_AUTHOR, System.getProperty("user.name"));
        parameters.put(PARAM_DATE, DateUtil.now());
        parameters.put(PARAM_VERSION, "1.0.0");
        parameters.put(PARAM_SINCE, "1.0.0");
        // Expose template utilities for Velocity usage
        parameters.put("util", new TemplateUtils());
        return parameters;
    }

    /**
     * 获取基础模板参数（公共参数）
     * <p>
     * 包括作者名、当前日期、版本号等所有模板共用的基础参数
     * </p>
     *
     * @return 基础参数映射 base template parameters
     * @since y.y.y
     */
    public Map<String, String> getBaseTemplateParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put(PARAM_AUTHOR, "作者名称");
        parameters.put(PARAM_DATE, "当前日期");
        parameters.put(PARAM_VERSION, "版本号");
        parameters.put(PARAM_STR, "Hutool字符串工具类");
        parameters.put(PARAM_SINCE, "起始版本");
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
