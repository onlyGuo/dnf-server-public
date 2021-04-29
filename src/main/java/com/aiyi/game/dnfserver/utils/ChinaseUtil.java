package com.aiyi.game.dnfserver.utils;

import com.aiyi.core.beans.PO;
import com.alibaba.fastjson.JSON;
import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 11:30
 */
public class ChinaseUtil {

    /**
     * 实体类中的字段转简体
     * @param po
     */
    public static void toSimple(PO po){
        Class<? extends PO> clazz = po.getClass();
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field: declaredFields){
            field.setAccessible(true);
            if (field.getAnnotation(Simple.class) != null){
                try {
                    field.set(po, toSimple(field.get(po).toString()));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 转换简体
     * @param str
     */
    public static String toSimple(String str){
        return ZhConverterUtil.toSimple(convertCharset(str));
    }

    /**
     * 实体类中的字段转繁体
     * @param po
     */
    public static void toTraditional(PO po){
        Class<? extends PO> clazz = po.getClass();
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field: declaredFields){
            field.setAccessible(true);
            if (field.getAnnotation(Simple.class) != null){
                try {
//                    String traditional = ZhConverterUtil.toTraditional(field.get(po).toString());
//                    byte[] latin1s = traditional.getBytes(StandardCharsets.UTF_8);
//                    String s = new String(latin1s, Charset.forName("latin1"));
                    field.set(po, toTraditional(field.get(po).toString()));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 转换繁体
     * @param str
     */
    public static String toTraditional(String str){
        String traditional = ZhConverterUtil.toTraditional(str);
        return convertCharsetUTF8(traditional);
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

    public static String convertCharsetUTF8(String s)
    {
        if (s != null)
        {
//            byte[] buffer = s.getBytes(StandardCharsets.UTF_8);
            //0x81 to Unicode 0x0081, 0x8d to 0x008d, 0x8f to 0x008f, 0x90 to 0x0090, and 0x9d to 0x009d.
            byte[] buffer = s.getBytes(StandardCharsets.UTF_8);
            String s1 = new String(buffer, Charset.forName("CP1252"));
            int length = s1.length();
            for (int i = 0; i < length; ++i)
            {
                char c = s1.charAt(i);
                if (c == 0x81)
                {
                    buffer[i] = (byte) 0x0081;
                }
                else if (c == 0x8d)
                {
                    buffer[i] = (byte) 0x008d;
                }
                else if (c == 0x8f)
                {
                    buffer[i] = (byte) 0x008f;
                }
                else if (c == 0x90)
                {
                    buffer[i] = (byte) 0x0090;
                }
                else if (c == 0x9d)
                {
                    buffer[i] = (byte) 0x009d;
                }
                else
                {
                    buffer[i] = Character.toString(c).getBytes(Charset.forName("CP1252"))[0];
                }
            }
            return new String(buffer, Charset.forName("CP1252"));
        }
        return null;
    }
}
