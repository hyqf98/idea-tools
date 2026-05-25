package io.github.ideatools.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import io.github.ideatools.service.mybatis.MapperXmlCacheService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Mapper Xml Startup Activity
 *
 * @author haijun
 * @date 2025-12-12 14:10:48
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class MapperXmlStartupActivity implements StartupActivity.DumbAware {

    /**
     * 项目启动后执行
     *
     * @param project 当前项目
     * @since 1.0.0
     */
    @Override
    public void runActivity(@NotNull Project project) {
        log.info("项目启动，开始扫描MyBatis Mapper XML文件: {}", project.getName());

        // 获取项目级别的缓存服务
        MapperXmlCacheService cacheService = MapperXmlCacheService.getInstance(project);

        // 使用智能模式启动时，等待索引完成后再扫描
        // 如果在 dumb 模式下，DumbService 会在索引完成后自动运行
        com.intellij.openapi.project.DumbService.getInstance(project).smartInvokeLater(() -> {
            // 异步执行扫描操作，避免阻塞项目启动
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                cacheService.scanAndCacheMapperXmlFiles();
            });
        });

        // 注册XML文件变化监听器
        MapperXmlChangeListener changeListener = new MapperXmlChangeListener(project);
        project.getMessageBus()
                .connect(project)
                .subscribe(VirtualFileManager.VFS_CHANGES, changeListener);

        log.info("MyBatis Mapper XML文件监听器已注册: {}", project.getName());
    }
}
