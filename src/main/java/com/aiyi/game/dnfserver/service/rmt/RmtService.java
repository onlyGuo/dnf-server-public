package com.aiyi.game.dnfserver.service.rmt;

import com.aiyi.core.exception.ServiceInvokeException;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.utils.Common;
import com.aiyi.game.dnfserver.utils.HttpUtils;
import com.aiyi.game.dnfserver.utils.MinFieldUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * 远程接口
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/29 10:46
 */
@Service
public class RmtService implements ApplicationListener<ContextRefreshedEvent> {

    private String thisIP;

    private String thisAddress;

    private String clientId;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                // 1. 初始化本地IP
                try {
                    JSONObject json = JSON.parseObject(HttpUtils.get("https://ip.cn/api/index?ip=&type=0"));
                    thisIP = json.getString("ip");
                    thisAddress = json.getString("address");
                }catch (Exception e){
                    throw new ServiceInvokeException("初始化失败: 无法定位本地IP");
                }

                // 2. 初始化客户端ID
                try {
                    clientId = new String(
                            MinFieldUtil.readFile("client"),
                            StandardCharsets.UTF_8
                    );
                }catch (Exception e){
                    clientId = UUID.randomUUID().toString().replace("-", "");
                    MinFieldUtil.writeFile("client", clientId);
                }

                // 3. 初始化默认管理员
                try {
                    User user = new User();
                    user.setAccount("admin");       // 这是默认管理员账号“admin”， 可直接修改为你的信息
                    user.setPassword("admin");      // 这是默认管理员密码“admin”， 可直接修改为你的信息
                    user.setIp(thisIP);
                    user.setClientId(clientId);
                    user.setAddress(Base64.getEncoder()
                            .encodeToString(thisAddress.getBytes(StandardCharsets.UTF_8)));
                    HttpUtils.post(Common.RMT_URL + "api/v1/init", user);
                }catch (Exception e){
                    throw new ServiceInvokeException("初始化失败: 无法注册默认管理员信息");
                }
            }
        }, 1000);

    }

    public String getThisIP() {
        return thisIP;
    }

    public void setThisIP(String thisIP) {
        this.thisIP = thisIP;
    }

    public String getThisAddress() {
        return thisAddress;
    }

    public void setThisAddress(String thisAddress) {
        this.thisAddress = thisAddress;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
