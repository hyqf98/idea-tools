package io.github.easy.tools.action.doc.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.psi.PsiFile;
import io.github.easy.tools.service.doc.processor.CommentProcessor;

/**
 * 使用AI生成文件注释操作类
 * <p>
 * 该类继承自AbstractEasyDocAction，实现了为整个Java文件使用AI生成注释的功能。
 * </p>
 */
public class GenerateFileCommentByAiAction extends AbstractEasyDocAction {

    /**
     * 动作执行方法，为整个文件使用AI生成注释
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

        // 使用AI生成文件注释
        processor.generateFileCommentByAi(file);
    }
}
