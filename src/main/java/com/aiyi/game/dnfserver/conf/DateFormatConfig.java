package com.aiyi.game.dnfserver.conf;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.cglib.core.Converter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * 全局Rest full API Date类型JSON 格式化
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/08 9:31
 */
@JsonComponent
public class DateFormatConfig {

    /**
     * 日期格式化
     */
    public static class DateJsonSerializer extends JsonSerializer<Date> {
        @Override
        public void serialize(Date date, JsonGenerator jsonGenerator,
                              SerializerProvider serializerProvider) throws IOException {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String format = dateFormat.format(date);
            jsonGenerator.writeString(format);
        }
    }

    /**
     * 解析日期字符串
     */
    public static class DateJsonDeserializer extends JsonDeserializer<Date> {
        @Override
        public Date deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException, JsonProcessingException {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                return dateFormat.parse(jsonParser.getText());
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }


    /**
     * Long 类型传给前端时转为String
     */
    public static class LongJsonSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long aLong, JsonGenerator jsonGenerator,
                              SerializerProvider serializerProvider) throws IOException {
            jsonGenerator.writeString(String.valueOf(aLong));
        }
    }

    /**
     * 解析Long类型字符串
     */
    public static class LongJsonDeserializer extends JsonDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException, JsonProcessingException {
            String str = jsonParser.getValueAsString();
            if (str == null){
                return null;
            }
            return Long.parseLong(str);
        }
    }
}
