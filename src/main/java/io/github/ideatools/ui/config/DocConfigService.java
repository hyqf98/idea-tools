package io.github.ideatools.ui.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import io.github.ideatools.constants.PromptConstants;
import io.github.ideatools.utils.TemplateUtils;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * 文档配置服务类 <p> 该类负责管理文档生成相关的配置信息，包括模板、自定义参数等。 使用IntelliJ平台的持久化机制来保存和加载配置。 </p>
 * <p>持久化范围：项目级（PROJECT级），不同项目可以有不同的配置</p>
 *
 * @author haijun
 * @date 2025-12-17 10:30:28
 * @version 1.0.0
 * @since 1.0.0
 */
@Service(Service.Level.PROJECT)
@State(name = "EasyDocConfig", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class DocConfigService implements PersistentStateComponent<DocConfigService> {

    /**
     * PARAM AUTHOR
     */
    public static final String PARAM_AUTHOR = "author";
    /**
     * PARAM DATE
     */
    public static final String PARAM_DATE = "date";
    /**
     * PARAM VERSION
     */
    public static final String PARAM_VERSION = "version";
    /**
     * PARAM DESCRIPTION
     */
    public static final String PARAM_DESCRIPTION = "description";

    /**
     * EMAIL
     */
    public static final String EMAIL = "email";

    /**
     * PARAM SINCE
     */
    public static final String PARAM_SINCE = "since";
    /**
     * PARAM STR
     */
    public static final String PARAM_STR = "str";
    /**
     * PARAM PARAMETERS
     */
    public static final String PARAM_PARAMETERS = "parameters";
    /**
     * PARAM RETURN TYPE
     */
    public static final String PARAM_RETURN_TYPE = "returnType";
    /**
     * PARAM RETURN TYPE SIMPLE
     */
    public static final String PARAM_RETURN_TYPE_SIMPLE = "returnTypeSimple";
    /**
     * PARAM EXCEPTIONS
     */
    public static final String PARAM_EXCEPTIONS = "exceptions";
    /**
     * PARAM FIELD NAME
     */
    public static final String PARAM_FIELD_NAME = "fieldName";
    /**
     * PARAM FIELD TYPE
     */
    public static final String PARAM_FIELD_TYPE = "fieldType";
    /**
     * PARAM EMAIL
     */
    public static final String PARAM_EMAIL = "email";
    /**
     * PARAM PACKAGE NAME
     */
    public static final String PARAM_PACKAGE_NAME = "packageName";




    /**
     * DEFAULT CLASS TEMPLATE
     */
    public static final String DEFAULT_CLASS_TEMPLATE = """
            /**
             * ${description}
             *
             #foreach( $param in $parameters )
             * @param $param.originalName $param.splitName
             #end
             * @author ${author}
             #if( $email && $email != "" )
             * @email "mailto:${email}"
             #end
             * @date ${date}
             * @version ${version}
             * @since ${since}
             */
            """;

    /**
     * DEFAULT METHOD TEMPLATE
     */
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

    /**
     * DEFAULT FIELD TEMPLATE
     */
    public static final String DEFAULT_FIELD_TEMPLATE = """
            /**
             * ${util.camelToWords($fieldName)}.
             */
            """;

    /**
     * DEFAULT PACKAGE TEMPLATE
     */
    public static final String DEFAULT_PACKAGE_TEMPLATE = """
            /**
             * ${description}
             *
             * @author ${author}
             #if( $email && $email != "" )
             * @email "mailto:${email}"
             #end
             * @date ${date}
             * @version ${version}
             * @since ${since}
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
     * 包注释模板
     */
    public String packageTemplate = DEFAULT_PACKAGE_TEMPLATE;

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
     * AI包注释生成提示词模板
     */
    public String packagePrompt = PromptConstants.DEFAULT_PACKAGE_PROMPT;

    /**
     * 自定义变量实体，用于持久化存储自定义变量的名称、描述和值
     *
     * @author haijun
     * @date 2025-12-17 10:30:28
     * @version 1.0.0
     * @since 1.0.0
     */
    @Data
    public static class CustomParam {
        /**
         * name
         */
        private String name;
        /**
         * description
         */
        private String description;
        /**
         * value
         */
        private String value;
    }

    /**
     * 获取基础参数列表 <p> 基础参数包括作者名、当前日期和空描述，这些参数会在所有模板中使用。 </p>
     *
     * @return 基础参数列表 base parameters
     * @since y.y.y
     */
    public Map<String, Object> getBaseParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        String userName = System.getProperty("user.name");
        parameters.put(PARAM_AUTHOR, userName);
        parameters.put(PARAM_DATE, DateFormatUtil.formatDateTime(new Date()));
        parameters.put(PARAM_VERSION, "1.0.0");
        parameters.put(PARAM_SINCE, "1.0.0");
        parameters.put(DocConfigService.EMAIL, userName + "@email.com");

        // Expose template utilities for Velocity usage
        parameters.put("util", new TemplateUtils());
        return parameters;
    }

    /**
     * 获取基础模板参数（公共参数） <p> 包括作者名、当前日期、版本号等所有模板共用的基础参数 </p>
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
     * 获取配置服务的项目实例
     *
     * @param project 项目实例
     * @return DocConfigService的项目实例 instance
     * @since y.y.y
     */
    public static DocConfigService getInstance(@NotNull Project project) {
        return project.getService(DocConfigService.class);
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
