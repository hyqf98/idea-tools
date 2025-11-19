package io.github.easy.tools.action.api;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import io.github.easy.tools.ui.api.ApiSearchDialog;
import org.jetbrains.annotations.NotNull;

/**
 * API搜索动作
 * 通过快捷键触发API搜索对话框，支持快速搜索和跳转到API接口
 * 
 * <p>功能说明：</p>
 * <ul>
 *   <li>注册为IDEA动作，可通过快捷键Ctrl+\触发</li>
 *   <li>创建并显示API搜索对话框</li>
 *   <li>只在有项目打开时启用</li>
 * </ul>
 * 
 * <p>集成说明：</p>
 * <ul>
 *   <li>通过plugin.xml中的action扩展点注册</li>
 *   <li>快捷键为Ctrl+\（Windows/Linux）或Cmd+\（Mac）</li>
 *   <li>添加到主菜单的"Code"组中</li>
 * </ul>
 * 
 * @author iamxiaohaijun
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiSearchDialog
 * @see AnAction
 */
public class ApiSearchAction extends AnAction {

    /**
     * 动作执行方法
     * 当用户触发此动作时调用，负责创建并显示API搜索对话框
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>从动作事件中获取当前项目实例</li>
     *   <li>如果项目不存在则直接返回</li>
     *   <li>创建ApiSearchDialog实例</li>
     *   <li>调用show()方法显示对话框</li>
     * </ol>
     * 
     * <p>注意事项：</p>
     * <ul>
     *   <li>确保在有项目打开时才执行动作</li>
     *   <li>使用模态对话框确保用户体验</li>
     * </ul>
     *
     * @param e 动作事件对象，包含触发动作时的上下文信息
     * @see ApiSearchDialog#show()
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        // 创建并显示API搜索对话框
        ApiSearchDialog dialog = new ApiSearchDialog(project);
        dialog.show();
    }

    /**
     * 动作更新方法
     * 控制动作的启用状态和可见性
     * 
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>从动作事件中获取当前项目实例</li>
     *   <li>只有在项目存在时才启用和显示此动作</li>
     * </ol>
     * 
     * <p>设计说明：</p>
     * <ul>
     *   <li>遵循IDEA动作设计规范</li>
     *   <li>只在有项目打开时启用，避免无效操作</li>
     * </ul>
     *
     * @param e 动作事件对象，包含触发动作时的上下文信息
     * @see AnAction#update(AnActionEvent)
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        // 只在有项目打开时启用此动作
        Project project = e.getProject();
        e.getPresentation().setEnabledAndVisible(project != null);
    }
}