package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.entity.RechargeKey;
import com.aiyi.game.dnfserver.service.RechargeKeyService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("api/v1/keys")
public class RechargeKeyController {

    @Resource
    private RechargeKeyService rechargeKeyService;

    /**
     * 制作卡密
     * @param key
     *      卡密信息
     */
    @PostMapping
    public void createKey(@RequestBody RechargeKey key){
        rechargeKeyService.create(key);
    }


    /**
     * 列出卡密列表
     * @param type
     *      卡密类型
     * @param status
     *      卡密状态
     * @param page
     *      页码
     * @param pageSize
     *      每页条数
     * @return
     */
    @GetMapping
    public ResultPage<RechargeKey> list(Integer type, Integer status,
                                        int page, int pageSize,
                                        String content){
        return rechargeKeyService.list(type, status, page, pageSize,
                content);
    }

    /**
     * 使用卡密
     * @param key
     */
    @PostMapping("use")
    @NoLogin
    public void use(@RequestBody RechargeKey key){
        rechargeKeyService.use(key);
    }

}
