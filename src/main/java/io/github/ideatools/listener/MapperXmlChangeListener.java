package io.github.ideatools.listener;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import io.github.ideatools.service.mybatis.MapperXmlCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Mapper Xml Change Listener
 *
 * @author haijun
 * @date 2025-12-12 14:08:31
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class MapperXmlChangeListener implements BulkFileListener {

    /**
     * 当前项目
     */
    private final Project project;

    /**
     * 文件变化后触发
     *
     * @param events 文件变化事件列表
     * @since 1.0.0
     */
    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
            VirtualFile virtualFile = event.getFile();
            if (virtualFile == null || !this.isXmlFile(virtualFile)) {
                continue;
            }

            // 处理不同的事件类型
            if (event instanceof VFileCreateEvent) {
                this.handleFileCreate(virtualFile);
            } else if (event instanceof VFileContentChangeEvent) {
                this.handleFileUpdate(virtualFile);
            } else if (event instanceof VFileDeleteEvent) {
                this.handleFileDelete(virtualFile);
            }
        }
    }

    /**
     * 判断是否为XML文件
     *
     * @param virtualFile 虚拟文件
     * @return true如果是XML文件
     * @since 1.0.0
     */
    private boolean isXmlFile(VirtualFile virtualFile) {
        return "xml".equalsIgnoreCase(virtualFile.getExtension());
    }

    /**
     * 处理文件创建事件
     *
     * @param virtualFile 创建的文件
     * @since 1.0.0
     */
    private void handleFileCreate(VirtualFile virtualFile) {
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                PsiFile psiFile = PsiManager.getInstance(this.project).findFile(virtualFile);
                if (!(psiFile instanceof XmlFile xmlFile)) {
                    return;
                }

                String namespace = this.extractNamespace(xmlFile);
                if (StringUtil.isNotEmpty(namespace)) {
                    MapperXmlCacheService.getInstance(this.project).addToCache(namespace, xmlFile);
                    log.debug("新增Mapper XML到缓存: namespace={}, file={}", namespace, virtualFile.getPath());
                }
            } catch (Exception e) {
                log.error("处理XML文件创建事件失败: " + virtualFile.getPath(), e);
            }
        });
    }

    /**
     * 处理文件更新事件
     *
     * @param virtualFile 更新的文件
     * @since 1.0.0
     */
    private void handleFileUpdate(VirtualFile virtualFile) {
        ApplicationManager.getApplication().runReadAction(() -> {
            try {
                PsiFile psiFile = PsiManager.getInstance(this.project).findFile(virtualFile);
                if (!(psiFile instanceof XmlFile xmlFile)) {
                    return;
                }

                String namespace = this.extractNamespace(xmlFile);
                if (StringUtil.isNotEmpty(namespace)) {
                    MapperXmlCacheService.getInstance(this.project).addToCache(namespace, xmlFile);
                    log.debug("更新Mapper XML缓存: namespace={}, file={}", namespace, virtualFile.getPath());
                }
            } catch (Exception e) {
                log.error("处理XML文件更新事件失败: " + virtualFile.getPath(), e);
            }
        });
    }

    /**
     * 处理文件删除事件
     *
     * @param virtualFile 删除的文件
     * @since 1.0.0
     */
    private void handleFileDelete(VirtualFile virtualFile) {
        // 文件已删除，无法获取PSI，通过文件路径从缓存中移除
        MapperXmlCacheService.getInstance(this.project).removeFromCacheByPath(virtualFile.getPath());
        log.debug("从缓存中移除Mapper XML: file={}", virtualFile.getPath());
    }

    /**
     * 从XML文件中提取namespace属性
     *
     * @param xmlFile XML文件
     * @return namespace值，如果不是Mapper文件或没有namespace则返回null
     * @since 1.0.0
     */
    private String extractNamespace(XmlFile xmlFile) {
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag == null) {
            return null;
        }

        // 检查根标签是否为mapper
        if (!"mapper".equals(rootTag.getName())) {
            return null;
        }

        // 获取namespace属性
        return rootTag.getAttributeValue("namespace");
    }
}
