package io.github.ideatools.action.doc.listener;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 文件保存监听器管理器
 * <p>
 * 用于动态注册和注销文件保存监听器，根据配置动态控制监听器的启用和禁用
 * </p>
 * <p>
 * 注意：由于配置已改为项目级别，监听器始终注册，但在处理时会根据具体项目的配置决定是否执行。
 * </p>
 * <p>
 * iamxiaohaijun
 *
 * @version 1.0.0
 * @email "mailto:iamxiaohaijun@gmail.com"
 * @date 2025.09.17 16:14
 * @since y.y.y
 */
public final class FileSaveListenerManager implements AppLifecycleListener {

    /** Listener */
    private FileDocumentManagerListener listener;
    /** Connection */
    private MessageBusConnection connection;
    /** Is registered */
    private boolean isRegistered = false;

    /**
     * App frame created
     * <p>
     * 应用启动时注册监听器。由于配置已改为项目级别，监听器始终注册，
     * 具体是否执行由监听器内部根据项目配置决定。
     * </p>
     *
     * @param commandLineArgs command line args
     * @since y.y.y
     */
    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        // 始终注册监听器，由监听器内部根据项目配置决定是否执行
        this.registerListener();
    }

    /**
     * 获取服务实例
     *
     * @return FileSaveListenerManager实例 instance
     * @since y.y.y
     */
    public static FileSaveListenerManager getInstance() {
        return ApplicationManager.getApplication().getService(FileSaveListenerManager.class);
    }

    /**
     * 更新监听器状态，根据配置决定是否注册或注销监听器
     * <p>
     * 注意：由于配置已改为项目级别，此方法现在主要用于确保监听器已注册。
     * 具体是否执行由监听器内部根据项目配置决定。
     * </p>
     *
     * @since y.y.y
     */
    public void updateListenerState() {
        // 确保监听器已注册
        if (!this.isRegistered) {
            this.registerListener();
        }
    }

    /**
     * 注册文件保存监听器
     *
     * @since y.y.y
     */
    private void registerListener() {
        if (this.listener == null) {
            this.listener = new FileSaveListener();
        }

        if (this.connection == null) {
            this.connection = ApplicationManager.getApplication().getMessageBus().connect();
        }

        this.connection.subscribe(FileDocumentManagerListener.TOPIC, this.listener);
        this.isRegistered = true;
    }
}
