package io.github.easy.tools.ui.api;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import io.github.easy.tools.ui.config.FeatureToggleService;
import org.jetbrains.annotations.NotNull;

/**
 * API管理工具窗口工厂类
 * 负责创建和初始化IDEA右侧工具栏中的API管理工具窗口
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>创建API管理面板实例</li>
 *   <li>将面板添加到工具窗口中</li>
 *   <li>处理IDE索引状态，确保在适当的时候初始化API数据</li>
 * </ul>
 * 
 * <p>集成说明：</p>
 * <ul>
 *   <li>通过plugin.xml中的toolWindow扩展点注册</li>
 *   <li>工具窗口ID为"API Manager"，停靠在IDE右侧</li>
 *   <li>使用新的UI API获取ContentFactory实例</li>
 * </ul>
 * 
 * @author iamxiaohaijun
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.11.18 15:48
 * @since 1.0.0
 * @see ApiManagerPanel
 * @see ToolWindowFactory
 */
public class ApiManagerToolWindowFactory implements ToolWindowFactory {

    /**
     * 创建工具窗口内容
     * 当用户打开API管理工具窗口时调用此方法，负责初始化窗口内容
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>创建API管理面板实例</li>
     *   <li>使用ContentFactory创建内容对象</li>
     *   <li>将面板添加到工具窗口的内容管理器中</li>
     *   <li>检查IDE是否处于dumb模式（索引未完成）</li>
     *   <li>如果索引完成则立即刷新API列表，否则等待索引完成后再刷新</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>通过DumbService检查避免在索引未完成时访问索引数据</li>
     *   <li>使用runWhenSmart方法确保在索引完成后执行初始化操作</li>
     * </ul>
     *
     * @param project    当前IntelliJ项目实例
     * @param toolWindow 工具窗口实例
     * @since 1.0.0
     * @see ApiManagerPanel#refreshApiList()
     * @see DumbService#isDumb(Project)
     * @see DumbService#runWhenSmart(Runnable)
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 创建API管理面板
        ApiManagerPanel apiManagerPanel = new ApiManagerPanel(project);

        // 创建内容工厂并添加面板到工具窗口
        Content content = ContentFactory.getInstance().createContent(apiManagerPanel, "", false);
        toolWindow.getContentManager().addContent(content);

        // 检查是否处于dumb模式（索引未完成），如果是则等待索引完成后再初始化
        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).runWhenSmart(() -> apiManagerPanel.refreshApiList());
        } else {
            // 初始化面板数据
            apiManagerPanel.refreshApiList();
        }
    }

    /**
     * 判断工具窗口是否应该可用
     * <p>
     * 根据功能开关配置决定是否显示此工具窗口
     * </p>
     *
     * @param project 当前项目
     * @return 如果功能启用返回true，否则返回false
     */
    @Override
    public boolean isApplicable(@NotNull Project project) {
        return FeatureToggleService.getInstance().isApiManagerToolWindowEnabled();
    }
}