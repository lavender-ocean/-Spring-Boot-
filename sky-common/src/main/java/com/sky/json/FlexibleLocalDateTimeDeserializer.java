package com.sky.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 灵活的 LocalDateTime 反序列化器
 * 兼容多种格式：
 *   - yyyy-MM-dd HH:mm:ss
 *   - yyyy-MM-dd HH:mm
 *   - yyyy-MM-dd'T'HH:mm:ss   (ISO 8601)
 *   - yyyy-MM-dd'T'HH:mm      (datetime-local 的默认格式)
 *   - ISO_LOCAL_DATE_TIME
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
    };

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        text = text.trim();

        // 逐个尝试格式
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个
            }
        }

        // 都不匹配，抛出明确异常
        throw new IOException("无法解析日期时间：" + text +
                "，支持的格式：yyyy-MM-dd HH:mm[:ss] 或 yyyy-MM-dd'T'HH:mm[:ss]");
    }
}