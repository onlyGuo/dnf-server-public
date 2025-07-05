package com.aiyi.game.dnfserver.utils;

import com.aiyi.core.beans.PO;
import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class CharsetUtil {

    // utf8转latin1
    public static String utf82latin1(String s) {
        if (s == null) {
            return null;
        }
        try {
            // 先以utf-8编码得到字节数组
            byte[] utf8Bytes = s.getBytes("UTF-8");
            // 再用latin1（ISO-8859-1）解读这些字节
            return new String(utf8Bytes, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("不支持的编码", e);
        }
    }

    /**
     * 实体类中的字段转简体
     * @param po
     */
    public static void latin12utf8(PO po){
        Class<? extends PO> clazz = po.getClass();
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field: declaredFields){
            field.setAccessible(true);
            if (field.getAnnotation(Simple.class) != null){
                try {
                    field.set(po, latin12utf8(field.get(po).toString()));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // latin1转utf8（常配合使用）
    public static String latin12utf8(String s) {
        if (s == null) {
            return null;
        }
        return ZhConverterUtil.toSimple(convertCharset(s));
    }

    public static String convertCharset(String s)
    {
        if (s != null)
        {
            int length = s.length();
            byte[] buffer = new byte[length];
            //0x81 to Unicode 0x0081, 0x8d to 0x008d, 0x8f to 0x008f, 0x90 to 0x0090, and 0x9d to 0x009d.
            for (int i = 0; i < length; ++i)
            {
                char c = s.charAt(i);
                if (c == 0x0081)
                {
                    buffer[i] = (byte) 0x81;
                }
                else if (c == 0x008d)
                {
                    buffer[i] = (byte) 0x8d;
                }
                else if (c == 0x008f)
                {
                    buffer[i] = (byte) 0x8f;
                }
                else if (c == 0x0090)
                {
                    buffer[i] = (byte) 0x90;
                }
                else if (c == 0x009d)
                {
                    buffer[i] = (byte) 0x9d;
                }
                else
                {
                    buffer[i] = Character.toString(c).getBytes(Charset.forName("CP1252"))[0];
                }
            }
            return new String(buffer, StandardCharsets.UTF_8);
        }
        return null;
    }

    // 测试例子
    public static void main(String[] args) {
        String s = "你好，世界！Hello World!";
        String latin1 = utf82latin1(s);
        System.out.println("Latin1编码内容: " + latin1);

        String back = latin12utf8(latin1);
        System.out.println("还原后: " + back);
    }
}