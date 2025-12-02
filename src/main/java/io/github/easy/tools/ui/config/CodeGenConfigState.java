package io.github.easy.tools.ui.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * <p> AI代码生成配置状态服务 </p>
 * <p>
 * 负责持久化管理“AI生成代码”相关的模板配置与数据源配置。
 * 模板支持增删改查、绑定数据源与选择的表、可配置自定义提示词与目标生成路径/文件名规则。
 * 数据源支持多套配置，供模板选择与生成时使用。
 * </p>
 *
 * <p>持久化范围：应用级（APP级）</p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
@Service(Service.Level.APP)
@State(name = "CodeGenConfigState", storages = @Storage("codegen-config-state.xml"))
public final class CodeGenConfigState implements PersistentStateComponent<CodeGenConfigState> {

    /**
     * 模板列表（保持顺序）
     */
    public List<TemplateConfig> templates = new ArrayList<>();

    /**
     * 当前选中的模板ID
     */
    public String currentTemplateId = "";

    /**
     * 数据源列表（可配置多套）
     */
    public List<DataSourceConfig> dataSources = new ArrayList<>();

    /**
     * 模板配置实体
     */
    @Data
    public static class TemplateConfig {
        /** 模板ID（UUID） */
        private String id = UUID.randomUUID().toString();
        /** 模板名称 */
        private String name = "";
        /** 参考文件路径（项目内 VirtualFile 路径字符串） */
        private String referenceFilePath = "";
        /** 生成文件名规则（支持占位符：${tableName}、${camelTableName}） */
        private String fileNamePattern = "${tableName}Generated.java";
        /** 目标生成目录（项目内相对路径） */
        private String targetDir = "";
        /** 是否使用自定义提示词 */
        private boolean useCustomPrompt = false;
        /** 自定义提示词内容（为空则使用默认提示词） */
        private String customPrompt = "";
        /** 绑定的数据源ID */
        private String dataSourceId = "";
        /** 选择的表名列表 */
        private List<String> tableNames = new ArrayList<>();
        /** 指定使用的大模型类型（如 openai、ollama 等） */
        private String selectedModelType = "";
    }

    /**
     * 数据源配置实体
     */
    @Data
    public static class DataSourceConfig {
        /** 数据源ID（UUID） */
        private String id = UUID.randomUUID().toString();
        /** 数据源名称（展示用） */
        private String name = "";
        /** JDBC URL */
        private String jdbcUrl = "";
        /** 用户名 */
        private String username = "";
        /** 密码 */
        private String password = "";
        /** 驱动类型（可选：mysql/postgresql/sqlserver/oracle 等） */
        private String driverType = "mysql";
    }

    /**
     * 获取单例实例
     *
     * @return CodeGenConfigState 单例
     */
    public static CodeGenConfigState getInstance() {
        return ApplicationManager.getApplication().getService(CodeGenConfigState.class);
    }

    /**
     * 根据模板ID查找模板
     *
     * @param id 模板ID
     * @return 模板Optional
     */
    public Optional<TemplateConfig> findTemplateById(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        return templates.stream().filter(t -> id.equals(t.getId())).findFirst();
    }

    /**
     * 设置当前选中模板ID
     *
     * @param id 模板ID
     */
    public void setCurrentTemplateId(String id) {
        this.currentTemplateId = id == null ? "" : id;
    }

    /**
     * 获取当前选中模板
     *
     * @return 模板Optional
     */
    public Optional<TemplateConfig> getCurrentTemplate() {
        return findTemplateById(this.currentTemplateId);
    }

    /**
     * 获取当前状态（用于持久化）
     *
     * @return 当前状态对象
     */
    @Override
    public CodeGenConfigState getState() {
        return this;
    }

    /**
     * 加载状态（用于持久化）
     *
     * @param state 要加载的状态
     */
    @Override
    public void loadState(@NotNull CodeGenConfigState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
