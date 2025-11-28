package io.github.easy.tools.ui.config;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * <p> API 测试配置状态（全局持久化） </p>
 * <p>
 * 管理 API 测试相关的全局配置，包括：
 * - 服务基础地址（例如：http://localhost:8080）
 * - 公共请求头（全局生效）
 * - 前置 HTTP 请求（如登录），及其 Token 提取规则
 * </p>
 * <p>
 * 配置为全局共享：在IDE中所有项目可用；Token 缓存不持久化，仅当前IDE会话有效。
 * </p>
 */
@Service(Service.Level.PROJECT)
@State(name = "EasyApiTestConfig", storages = @Storage(com.intellij.openapi.components.StoragePathMacros.WORKSPACE_FILE))
public class ApiTestConfigState implements PersistentStateComponent<ApiTestConfigState> {

    /** 服务基础地址，如：http://localhost:8080 */
    public String baseUrl = "";

    /** 公共请求头 */
    public List<HeaderItem> commonHeaders = new ArrayList<>();

    /** 是否启用前置请求（如登录） */
    public boolean preRequestEnabled = false;

    /** 前置请求配置 */
    public PreRequestConfig preRequest = new PreRequestConfig();

    @Data
    @NoArgsConstructor
    public static class HeaderItem {
        /** 头名称 */
        private String name;
        /** 头值（支持常量或从接口返回的变量组装） */
        private String value;
    }

    @Data
    @NoArgsConstructor
    public static class PreRequestConfig {
        /** 登录接口URL（绝对或相对） */
        private String url;
        /** 方法：GET/POST/PUT/DELETE */
        private String method = "POST";
        /** 请求体（JSON） */
        private String bodyJson;
        /** 自定义请求头 */
        private List<HeaderItem> headers = new ArrayList<>();
        /** Token在响应JSON中的提取路径，如：data.token 或 access_token */
        private String tokenJsonPath = "access_token";
        /** Token挂载到请求头的名称，如：Authorization */
        private String tokenHeaderName = "Authorization";
        /** 是否自动添加 Bearer 前缀 */
        private boolean useBearer = true;
        /** Token 过期分钟数（会话内），默认 120 分钟 */
        private int tokenExpireMinutes = 120;
    }

    /** 获取项目级单例 */
    public static ApiTestConfigState getInstance(Project project) {
        return project.getService(ApiTestConfigState.class);
    }

    @Override
    public ApiTestConfigState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ApiTestConfigState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
