package com.aiyi.game.dnfserver.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 小文件操作工具类类
 * @author gsk
 */
public class MinFieldUtil {

    /**
     * 缓存容器
     */
    private static Map<String, CacheByte> sourceCacheMap = new HashMap<>();

    /**
     * 写文件
     * @param file
     *      文件名
     * @param content
     *      文件内容
     */
    public static void writeFile(String file, String content) {
        try (FileOutputStream out = new FileOutputStream(file)){
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    /**
     * 本地资源文件缓存实体
     */
    private static class CacheByte{
        byte[] bytes;
        long cacheTime;
    }



    /**
     * 读入一个文件到内存
     * @param file
     *          要读入的文件
     * @return byte[]
     *          文件字节码
     */
    public static byte[] readFile(File file){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try{
            FileInputStream in = new FileInputStream(file);
            byte[] buffer = new byte[2048];
            for(int i = in.read(buffer); i > 0; i = in.read(buffer)){
                out.write(buffer, 0, i);
            }
            in.close();
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    /**
     * 读入一个文件到内存
     * @param filePath
     *          要读入的文件路径
     * @return byte[]
     *          文件字节码
     */
    public static byte[] readFile(String filePath){
        return readFile(new File(filePath));
    }

    /**
     * 读入一个当前项目下的资源文件
     * @param filePath
     *          资源文件路径
     * @return byte[]
     *          文件字节码
     */
    public static byte[] readResource(String filePath){
        filePath = filePath.replace("classpath:", "");
        ClassPathResource classpathResource = new ClassPathResource(filePath);
        CacheByte cacheByte = sourceCacheMap.get(filePath);
        // 缓存10秒
        if (null != cacheByte && cacheByte.cacheTime + 10000 > System.currentTimeMillis()){
            return cacheByte.bytes;
        }
        // 否则读文件
//        File file = null;
//        try {
//            file = ResourceUtils.getFile("classpath:" + filePath);
//        }catch (FileNotFoundException e){
//            throw new RuntimeException(e);
//        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try(InputStream stream = classpathResource.getInputStream()){
            for(int i = stream.read(buffer); i > 0; i = stream.read(buffer)){
                out.write(buffer, 0, i);
            }
        }catch (IOException e){
            throw new RuntimeException(e);
        }
        byte[] result = out.toByteArray();

        // 添加到缓存
        cacheByte = new MinFieldUtil.CacheByte();
        cacheByte.bytes = result;
        cacheByte.cacheTime = System.currentTimeMillis();
        sourceCacheMap.put(filePath, cacheByte);

        return result;
    }
}
