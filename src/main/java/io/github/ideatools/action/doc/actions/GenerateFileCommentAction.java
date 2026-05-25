package io.github.ideatools.action.doc.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.psi.PsiFile;
import io.github.ideatools.service.doc.processor.CommentProcessor;
import io.github.ideatools.ui.config.FeatureToggleService;
import org.jetbrains.annotations.NotNull;

/**
 * 生成文件注释操作类
 * <p>
 * 该类继承自AbstractEasyDocAction，实现了为整个Java文件生成注释的功能。
 * 它会遍历文件中的所有可注释元素，并为它们生成相应的文档注释。
 * </p>
 */
public class GenerateFileCommentAction extends AbstractEasyDocAction {

    /**
     * 动作执行方法，为整个文件生成注释
     * <p>
     * 该方法会在IDEA界面中添加一个菜单项，当用户点击时会执行文件注释生成操作。
     * 它会获取当前文件信息，然后为文件中的所有可注释元素生成注释。
     * </p>
     *
     * @param e 动作事件对象，包含执行环境信息
     */
    @Override
    public void actionPerformed(AnActionEvent e) {
        // 获取当前文件
        PsiFile file = e.getData(CommonDataKeys.PSI_FILE);

        // 获取注释处理器
        CommentProcessor processor = this.getProcessor(file);
        if (processor == null) {
            return;
        }

        // 生成文件注释
        processor.generateFileComment(file);
    }

    /**
     * 更新动作状态
     * <p>
     * 根据功能开关配置决定是否启用此动作
     * </p>
     *
     * @param e 动作事件对象
     */
    /**
     * 指定在后台线程更新动作状态
     *
     * @return 后台线程
     */
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(
                FeatureToggleService.getInstance().isGenerateFileCommentEnabled()
        );
    }
}