package com.aiyi.game.dnfserver.utils;

import com.aiyi.core.exception.ServiceInvokeException;
import com.aiyi.game.dnfserver.dao.User;
import com.alibaba.fastjson.JSON;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/29 10:57
 */
public class HttpUtils {

    private static CloseableHttpClient client = HttpClients.createDefault();

    /**
     * Get方法
     * @param url
     *      远程地址
     * @return
     */
    public static String get(String url){
        try (CloseableHttpResponse res = client.execute(new HttpGet(url))){
            return EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new ServiceInvokeException("获取远程服务信息失败");
    }

    /**
     * POST 方法
     * @param url
     *      远程地址
     * @param param
     *      提交参数
     *
     * @return
     */
    public static String post(String url, Object param) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new StringEntity(JSON.toJSONString(param), StandardCharsets.UTF_8));
        httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
        try (CloseableHttpResponse res = client.execute(httpPost)){
            return EntityUtils.toString(res.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new ServiceInvokeException("获取远程服务信息失败");
    }
}
