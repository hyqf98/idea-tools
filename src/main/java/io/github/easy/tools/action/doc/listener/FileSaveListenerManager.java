package io.github.easy.tools.action.doc.listener;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import io.github.easy.tools.ui.config.DocConfigService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 文件保存监听器管理器
 * <p>
 * 用于动态注册和注销文件保存监听器，根据配置动态控制监听器的启用和禁用
 * </p>
 *
 * iamxiaohaijun
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
     *
     * @param commandLineArgs command line args
     * @since y.y.y
     */
    @Override
    public void appFrameCreated(@NotNull List<String> commandLineArgs) {
        // 初始化时根据配置决定是否注册监听器
        if (DocConfigService.getInstance().saveListener) {
            this.registerListener();
        }
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
     *
     * @since y.y.y
     */
    public void updateListenerState() {
        boolean shouldRegister = DocConfigService.getInstance().saveListener;

        if (shouldRegister && !this.isRegistered) {
            this.registerListener();
        } else if (!shouldRegister && this.isRegistered) {
            this.unregisterListener();
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

    /**
     * 注销文件保存监听器
     *
     * @since y.y.y
     */
    private void unregisterListener() {
        if (this.connection != null && this.isRegistered) {
            // 断开连接以注销监听器
            this.connection.disconnect();
            this.connection = null;
            this.isRegistered = false;
        }
    }

    /**
     * 检查监听器是否已注册
     *
     * @return 如果已注册返回true ，否则返回false
     * @since y.y.y
     */
    public boolean isRegistered() {
        return this.isRegistered;
    }
}
