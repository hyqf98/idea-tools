package io.github.easy.tools.entity.doc;

import lombok.Builder;
import lombok.Data;

/**
 * 参数信息实体类
 * <p>
 * 用于存储方法参数和返回值的详细信息，包括短名称、首字母小写、分割后的名称、参数的类型完全限定名等。
 * </p>
 */
@Data
@Builder
public class ParameterInfo {
    /**
     * 参数/返回值的原始名称
     */
    @Desc("参数/返回值的原始名称")
    private String originalName;

    /**
     * 短名称（简单名称）
     */
    @Desc("短名称（简单名称）")
    private String shortName;

    /**
     * 首字母小写的名称
     */
    @Desc("首字母小写的名称")
    private String lowerFirstName;

    /**
     * 分割后的名称（根据驼峰命名规则分割）
     */
    @Desc("分割后的名称（根据驼峰命名规则分割）")
    private String splitName;

    /**
     * 参数的类型完全限定名
     */
    @Desc("参数的类型完全限定名")
    private String qualifiedTypeName;

    /**
     * 参数的类型简单名称
     */
    @Desc("参数的类型简单名称")
    private String simpleTypeName;
}
