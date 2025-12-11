package io.github.easy.tools.action.doc.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import io.github.easy.tools.service.doc.processor.JavaCommentProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 文件创建监听器
 * <p>
 * 监听Java文件的创建事件,在新建Java文件时自动生成类注释。
 * 完全复用JavaCommentProcessor的注释生成逻辑,确保与手动生成的格式完全一致。
 * </p>
 * <p>
 * 工作流程:
 * 1. 监听VirtualFile创建事件
 * 2. 检查是否为Java文件
 * 3. 获取文件中的第一个类
 * 4. 调用JavaCommentProcessor生成注释
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class FileCreationListener implements BulkFileListener {

    /**
     * Java注释处理器
     */
    private static final JavaCommentProcessor JAVA_PROCESSOR = new JavaCommentProcessor();

    /**
     * 文件系统事件发生后的回调
     * <p>
     * 监听文件创建事件,当检测到新建Java文件时自动生成类注释。
     * </p>
     *
     * @param events 文件事件列表
     * @since 1.0.0
     */
    @Override
    public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        for (VFileEvent event : events) {
            // 只处理文件创建事件
            if (!(event instanceof VFileCreateEvent)) {
                continue;
            }

            VirtualFile file = event.getFile();
            if (file == null || !file.isValid()) {
                continue;
            }

            // 只处理Java文件
            if (!"java".equalsIgnoreCase(file.getExtension())) {
                continue;
            }

            // 延迟执行,确保文件完全创建且PSI完全解析
            ApplicationManager.getApplication().invokeLater(() -> {
                // 获取打开的项目
                Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
                if (openProjects.length == 0) {
                    return;
                }
                
                // 使用第一个打开的项目
                Project project = openProjects[0];
                
                // 再次延迟,确保PSI完全初始化
                ApplicationManager.getApplication().invokeLater(() -> {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        try {
                            this.generateCommentForNewFile(file, project);
                        } catch (Exception e) {
                            // 静默处理异常,不影响文件创建
                            e.printStackTrace();
                        }
                    });
                });
            });
        }
    }

    /**
     * 为新建的Java文件生成注释
     * <p>
     * 先删除IDEA模板生成的注释,再使用JavaCommentProcessor生成新的注释。
     * 这样可以确保使用插件配置的注释模板,而不是IDEA内置模板。
     * </p>
     *
     * @param virtualFile 虚拟文件
     * @param project 项目实例
     * @since 1.0.0
     */
    private void generateCommentForNewFile(VirtualFile virtualFile, Project project) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (!(psiFile instanceof PsiJavaFile)) {
            return;
        }

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        PsiClass[] classes = javaFile.getClasses();
        
        if (classes.length == 0) {
            return;
        }

        // 获取第一个类(通常新建文件只有一个类)
        PsiClass firstClass = classes[0];
        
        // 先删除IDEA模板生成的注释
        JAVA_PROCESSOR.removeElementComment(psiFile, firstClass);
        
        // 使用插件配置的模板生成新注释
        // 使用overwrite=false,因为上一步已经删除了注释
        JAVA_PROCESSOR.generateElementComment(psiFile, firstClass, false);
    }
}
