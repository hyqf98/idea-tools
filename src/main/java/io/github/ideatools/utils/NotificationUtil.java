package io.github.ideatools.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;

/**
 * 通知工具类
 * <p>
 * 用于统一处理IDEA中的通知消息，包括错误、警告和信息提示。
 * 所有通知默认在5秒后自动消失。
 * </p>
 *
 * @author haijun
 * @since 1.0.0
 */
public class NotificationUtil {

    /**
     * 通知组名称
     */
    private static final String NOTIFICATION_GROUP = "Easy Docs Notification Group";

    /**
     * 默认通知显示时间（毫秒）
     * 5秒后自动消失
     */
    private static final int DEFAULT_EXPIRE_TIME_MS = 5000;

    /**
     * 显示错误通知
     * <p>
     * 通知将在5秒后自动消失
     * </p>
     *
     * @param project 项目实例
     * @param message 错误消息
     */
    public static void showError(Project project, String message) {
        showNotification(project, message, NotificationType.ERROR);
    }

    /**
     * 显示警告通知
     * <p>
     * 通知将在5秒后自动消失
     * </p>
     *
     * @param project 项目实例
     * @param message 警告消息
     */
    public static void showWarning(Project project, String message) {
        showNotification(project, message, NotificationType.WARNING);
    }

    /**
     * 显示信息通知
     * <p>
     * 通知将在5秒后自动消失
     * </p>
     *
     * @param project 项目实例
     * @param message 信息消息
     */
    public static void showInfo(Project project, String message) {
        showNotification(project, message, NotificationType.INFORMATION);
    }

    /**
     * 显示自定义过期时间的错误通知
     *
     * @param project 项目实例
     * @param message 错误消息
     * @param expireTimeMs 过期时间（毫秒）
     */
    public static void showError(Project project, String message, int expireTimeMs) {
        showNotification(project, message, NotificationType.ERROR, expireTimeMs);
    }

    /**
     * 显示自定义过期时间的警告通知
     *
     * @param project 项目实例
     * @param message 警告消息
     * @param expireTimeMs 过期时间（毫秒）
     */
    public static void showWarning(Project project, String message, int expireTimeMs) {
        showNotification(project, message, NotificationType.WARNING, expireTimeMs);
    }

    /**
     * 显示自定义过期时间的信息通知
     *
     * @param project 项目实例
     * @param message 信息消息
     * @param expireTimeMs 过期时间（毫秒）
     */
    public static void showInfo(Project project, String message, int expireTimeMs) {
        showNotification(project, message, NotificationType.INFORMATION, expireTimeMs);
    }

    /**
     * 显示通知（使用默认过期时间）
     *
     * @param project 项目实例
     * @param message 通知消息
     * @param type 通知类型
     */
    private static void showNotification(Project project, String message, NotificationType type) {
        showNotification(project, message, type, DEFAULT_EXPIRE_TIME_MS);
    }

    /**
     * 显示通知（使用自定义过期时间）
     *
     * @param project 项目实例
     * @param message 通知消息
     * @param type 通知类型
     * @param expireTimeMs 过期时间（毫秒）
     */
    private static void showNotification(Project project, String message, NotificationType type, int expireTimeMs) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(message, type);
        
        // 使用 IntelliJ Platform 的定时器在指定时间后自动过期通知
        notification.whenExpired(() -> notification.expire());
        
        notification.notify(project);
        
        // 延迟指定时间后自动隐藏通知
        com.intellij.util.concurrency.AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(notification::expire, expireTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}