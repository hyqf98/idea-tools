package io.github.easy.tools.entity.doc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段描述注解
 * <p>
 * 用于为类字段提供描述信息，主要用于文档生成和配置界面显示
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Desc {
    /**
     * 字段描述信息
     *
     * @return 描述文本
     */
    String value();
}