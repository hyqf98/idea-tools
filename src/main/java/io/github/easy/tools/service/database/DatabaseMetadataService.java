package io.github.easy.tools.service.database;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import io.github.easy.tools.ui.config.CodeGenConfigState;
import lombok.Builder;
import lombok.Data;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <p> 数据库元数据服务 </p>
 * <p>
 * 提供数据库连接测试、表列表获取、表结构查询、DDL语句生成等功能。
 * 支持主流数据库：MySQL、PostgreSQL、SQL Server、Oracle 等。
 * </p>
 *
 * @author haijun
 * @version 1.0.0
 * @since 1.0.0
 */
public class DatabaseMetadataService {

    /**
     * 测试数据源连接
     *
     * @param dataSource 数据源配置
     * @return 测试结果（成功/失败信息）
     */
    public String testConnection(CodeGenConfigState.DataSourceConfig dataSource) {
        if (dataSource == null) {
            return "数据源配置为空";
        }

        if (StrUtil.isBlank(dataSource.getJdbcUrl())) {
            return "JDBC URL 不能为空";
        }

        try {
            this.loadDriver(dataSource.getDriverType());
            try (Connection connection = this.createConnection(dataSource)) {
                if (connection != null && !connection.isClosed()) {
                    DatabaseMetaData metaData = connection.getMetaData();
                    String dbName = metaData.getDatabaseProductName();
                    String dbVersion = metaData.getDatabaseProductVersion();
                    return String.format("连接成功！数据库：%s，版本：%s", dbName, dbVersion);
                }
            }
        } catch (Exception e) {
            return "连接失败：" + e.getMessage();
        }
        return "连接失败";
    }

    /**
     * 获取指定数据源下的所有表名
     *
     * @param dataSource 数据源配置
     * @return 表名列表
     * @throws SQLException SQL异常
     */
    public List<String> listTables(CodeGenConfigState.DataSourceConfig dataSource) throws SQLException {
        List<String> tables = new ArrayList<>();
        if (dataSource == null || StrUtil.isBlank(dataSource.getJdbcUrl())) {
            return tables;
        }

        this.loadDriver(dataSource.getDriverType());
        try (Connection connection = this.createConnection(dataSource)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schemaPattern = this.getSchemaPattern(dataSource, connection);

            try (ResultSet rs = metaData.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (StrUtil.isNotBlank(tableName)) {
                        tables.add(tableName);
                    }
                }
            }
        }
        return tables;
    }

    /**
     * 获取指定表的结构信息
     *
     * @param dataSource 数据源配置
     * @param tableName  表名
     * @return 表结构信息
     * @throws SQLException SQL异常
     */
    public TableMetadata getTableMetadata(CodeGenConfigState.DataSourceConfig dataSource, String tableName) throws SQLException {
        if (dataSource == null || StrUtil.isBlank(tableName)) {
            return null;
        }

        this.loadDriver(dataSource.getDriverType());
        try (Connection connection = this.createConnection(dataSource)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schemaPattern = this.getSchemaPattern(dataSource, connection);

            TableMetadata tableMetadata = TableMetadata.builder()
                    .tableName(tableName)
                    .columns(new ArrayList<>())
                    .build();

            // 获取表注释
            try (ResultSet tableRs = metaData.getTables(catalog, schemaPattern, tableName, new String[]{"TABLE"})) {
                if (tableRs.next()) {
                    tableMetadata.setTableComment(tableRs.getString("REMARKS"));
                }
            }

            // 获取主键信息
            List<String> primaryKeys = new ArrayList<>();
            try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, schemaPattern, tableName)) {
                while (pkRs.next()) {
                    primaryKeys.add(pkRs.getString("COLUMN_NAME"));
                }
            }

            // 获取列信息
            try (ResultSet columnsRs = metaData.getColumns(catalog, schemaPattern, tableName, "%")) {
                while (columnsRs.next()) {
                    String columnName = columnsRs.getString("COLUMN_NAME");
                    ColumnMetadata column = ColumnMetadata.builder()
                            .columnName(columnName)
                            .columnType(columnsRs.getString("TYPE_NAME"))
                            .columnSize(columnsRs.getInt("COLUMN_SIZE"))
                            .nullable(columnsRs.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
                            .columnComment(columnsRs.getString("REMARKS"))
                            .isPrimaryKey(primaryKeys.contains(columnName))
                            .build();
                    tableMetadata.getColumns().add(column);
                }
            }

            return tableMetadata;
        }
    }

    /**
     * 获取指定表的 DDL 语句（简化版，基于表结构生成）
     *
     * @param dataSource 数据源配置
     * @param tableName  表名
     * @return DDL 语句
     */
    public String getTableDDL(CodeGenConfigState.DataSourceConfig dataSource, String tableName) {
        try {
            TableMetadata metadata = this.getTableMetadata(dataSource, tableName);
            if (metadata == null || CollUtil.isEmpty(metadata.getColumns())) {
                return "-- 无法获取表结构信息";
            }

            StringBuilder ddl = new StringBuilder();
            ddl.append("CREATE TABLE ").append(tableName).append(" (\n");

            List<String> primaryKeys = new ArrayList<>();
            for (int i = 0; i < metadata.getColumns().size(); i++) {
                ColumnMetadata column = metadata.getColumns().get(i);
                ddl.append("  ").append(column.getColumnName())
                        .append(" ").append(column.getColumnType());

                if (column.getColumnSize() > 0 && this.needsSize(column.getColumnType())) {
                    ddl.append("(").append(column.getColumnSize()).append(")");
                }

                if (!column.isNullable()) {
                    ddl.append(" NOT NULL");
                }

                if (StrUtil.isNotBlank(column.getColumnComment())) {
                    ddl.append(" COMMENT '").append(column.getColumnComment()).append("'");
                }

                if (i < metadata.getColumns().size() - 1) {
                    ddl.append(",");
                }
                ddl.append("\n");

                if (column.isPrimaryKey()) {
                    primaryKeys.add(column.getColumnName());
                }
            }

            if (!primaryKeys.isEmpty()) {
                ddl.append(",\n  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")\n");
            }

            ddl.append(")");

            if (StrUtil.isNotBlank(metadata.getTableComment())) {
                ddl.append(" COMMENT='").append(metadata.getTableComment()).append("'");
            }
            ddl.append(";");

            return ddl.toString();
        } catch (Exception e) {
            return "-- 生成 DDL 失败：" + e.getMessage();
        }
    }

    /**
     * 加载数据库驱动
     *
     * @param driverType 驱动类型
     */
    private void loadDriver(String driverType) {
        try {
            String driverClass = this.getDriverClass(driverType);
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("无法加载数据库驱动：" + driverType + "，请检查依赖配置", e);
        }
    }

    /**
     * 根据驱动类型获取驱动类名
     *
     * @param driverType 驱动类型
     * @return 驱动类名
     */
    private String getDriverClass(String driverType) {
        return switch (StrUtil.blankToDefault(driverType, "mysql").toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            default -> "com.mysql.cj.jdbc.Driver";
        };
    }

    /**
     * 创建数据库连接
     *
     * @param dataSource 数据源配置
     * @return 数据库连接
     * @throws SQLException SQL异常
     */
    private Connection createConnection(CodeGenConfigState.DataSourceConfig dataSource) throws SQLException {
        return DriverManager.getConnection(
                dataSource.getJdbcUrl(),
                dataSource.getUsername(),
                dataSource.getPassword()
        );
    }

    /**
     * 获取 Schema 模式（不同数据库处理方式不同）
     *
     * @param dataSource 数据源配置
     * @param connection 数据库连接
     * @return Schema 模式
     */
    private String getSchemaPattern(CodeGenConfigState.DataSourceConfig dataSource, Connection connection) {
        try {
            String driverType = dataSource.getDriverType();
            if ("postgresql".equalsIgnoreCase(driverType)) {
                return "public";
            } else if ("oracle".equalsIgnoreCase(driverType)) {
                return dataSource.getUsername().toUpperCase();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 判断字段类型是否需要长度
     *
     * @param columnType 字段类型
     * @return 是否需要长度
     */
    private boolean needsSize(String columnType) {
        if (StrUtil.isBlank(columnType)) {
            return false;
        }
        String type = columnType.toUpperCase();
        return type.contains("CHAR") || type.contains("VARCHAR") || type.contains("DECIMAL");
    }

    /**
     * 表元数据信息
     */
    @Data
    @Builder
    public static class TableMetadata {
        /** 表名 */
        private String tableName;
        /** 表注释 */
        private String tableComment;
        /** 列信息列表 */
        private List<ColumnMetadata> columns;
    }

    /**
     * 列元数据信息
     */
    @Data
    @Builder
    public static class ColumnMetadata {
        /** 列名 */
        private String columnName;
        /** 列类型 */
        private String columnType;
        /** 列长度 */
        private int columnSize;
        /** 是否可为空 */
        private boolean nullable;
        /** 列注释 */
        private String columnComment;
        /** 是否为主键 */
        private boolean isPrimaryKey;
    }
}
