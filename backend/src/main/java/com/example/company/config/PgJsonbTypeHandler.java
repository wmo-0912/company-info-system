package com.example.company.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

/**
 * PostgreSQL JSONB 字段处理器：写入时使用 setObject(Types.OTHER)，
 * 避免 JacksonTypeHandler 的 setString 导致 "jsonb vs character varying" 错误。
 */
@MappedTypes(Map.class)
public class PgJsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Class<?> type;

    public PgJsonbTypeHandler(Class<?> type) {
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setObject(i, MAPPER.writeValueAsString(parameter), Types.OTHER);
        } catch (Exception e) {
            throw new SQLException("JSON 序列化失败", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Map<String, Object> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new SQLException("JSON 反序列化失败: " + json, e);
        }
    }
}
