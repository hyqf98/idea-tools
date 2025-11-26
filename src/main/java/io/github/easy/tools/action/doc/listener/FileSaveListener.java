package io.github.easy.tools.action.doc.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import io.github.easy.tools.service.doc.processor.CommentProcessor;
import io.github.easy.tools.service.doc.processor.JavaCommentProcessor;
import io.github.easy.tools.ui.config.DocConfigService;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件保存监听器，用于在文件保存时自动处理注释
 * <p>
 * 该监听器会在文件保存时自动检查是否需要生成或更新注释。
 * 它会根据配置决定是否启用此功能，并只对Java文件进行处理。
 * </p>
 */
public class FileSaveListener implements FileDocumentManagerListener {

    /**
     * 注释处理器映射
     */
    private static final Map<String, CommentProcessor> PROCESSOR_MAP = new HashMap<>();

    static {
        PROCESSOR_MAP.put("JAVA", new JavaCommentProcessor());
    }

    /**
     * 文档保存前的回调方法
     * <p>
     * 在文档保存前检查是否需要自动处理注释。
     * 只有在启用保存监听器且文件类型为Java时才会执行处理。
     * </p>
     *
     * @param document 保存的文档
     */
    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        // 检查是否启用保存监听器
        if (!DocConfigService.getInstance().saveListener) {
            return;
        }

        // 获取虚拟文件
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
        if (virtualFile == null) {
            return;
        }

        // 只处理Java文件
        if (!"JAVA".equals(virtualFile.getFileType().getName())) {
            return;
        }

        // 获取项目和PsiFile
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length == 0) {
            return;
        }
        
        Project project = projects[0]; // 使用第一个打开的项目

        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (psiFile == null) {
            return;
        }

        // 获取注释处理器并异步生成注释
        CommentProcessor processor = PROCESSOR_MAP.get(virtualFile.getFileType().getName());
        if (processor != null) {
            // 使用invokeLater将PSI修改操作推迟到保存操作完成后
            ApplicationManager.getApplication().invokeLater(() -> {
                // 确保文档已经提交到PSI
                PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
                if (!psiDocumentManager.isCommitted(document)) {
                    // 如果文档尚未提交，则先提交文档
                    psiDocumentManager.commitDocument(document);
                }
                // 只有当元素没有注释时才生成注释
                processor.generateFileComment(psiFile, false);
            });
        }
    }
}