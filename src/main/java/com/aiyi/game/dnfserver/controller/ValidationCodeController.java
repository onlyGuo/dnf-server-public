package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultBean;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.sql.where.C;
import com.aiyi.core.util.CodeUtil;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.service.SmsService;
import com.aiyi.game.dnfserver.utils.cache.CacheUtil;
import com.aiyi.game.dnfserver.utils.cache.Key;
import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 郭胜凯
 * @Date: 2020/10/28 11:20
 * @Email 719348277@qq.com
 * @Description: 验证码相关接口
 */
@RestController
@RequestMapping("api/v1/vc")
public class ValidationCodeController {

    @Resource
    private AccountVODao accountVODao;

    @Resource
    private SmsService smsService;

    /**
     * 取验证码
     * @param index
     *      验证码索引
     * @return
     */
    @GetMapping("{index}")
    @NoLogin
    public ResultBean validation(@PathVariable String index){
        int rand = CodeUtil.rand(10, 99);
        CacheUtil.put(Key.as(CommonAttr.CACHE.VALIDATION_CODE, index), rand * 288, TimeUnit.MINUTES, 5);
        return ResultBean.success().setResponseBody(rand);
    }

    /**
     * 图形验证码
     * @param index
     */
    @GetMapping("img/{index}")
    @NoLogin
    public void imgCode(@PathVariable String index, HttpServletResponse response){
        SpecCaptcha specCaptcha = new SpecCaptcha(130, 48, 5);
        // 设置类型，纯数字、纯字母、字母数字混合
        specCaptcha.setCharType(Captcha.TYPE_NUM_AND_UPPER);
        String code = specCaptcha.text().toLowerCase();
        CacheUtil.put(Key.as(CommonAttr.CACHE.VALIDATION_CODE, index), code, TimeUnit.MINUTES, 5);
        response.setContentType("image/gif");
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "no-cache");
        response.setDateHeader("Expires", 0);
        try (OutputStream out = response.getOutputStream()){
            specCaptcha.out(out);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * 发送图片验证码
     * @param param
     *      参数
     * @return
     */
    @PostMapping("sms")
    @NoLogin
    public ResultBean smsCode(@RequestBody Map<String, String> param){
        String index = param.get("index");
        String code = param.get("code");
        String s = CacheUtil.get(Key.as(CommonAttr.CACHE.VALIDATION_CODE, index), String.class);
        if (StringUtils.isEmpty(s) || !s.equalsIgnoreCase(code)){
            throw new ValidationException("图形验证码不正确或已过期");
        }
        AccountVO phone = accountVODao.get(Method.where(AccountVO::getAccountname, C.EQ, param.get("phone")));
        if ("Register".equals(param.get("type"))){
            // 注册验证码，判断账户存在则不发送
            if (phone != null){
                throw new ValidationException("该手机号已被注册, 请更换其他手机号");
            }
        }else{
            // 通用验证码，判断账户不存在则不发送
            if (phone == null){
                throw new ValidationException("该手机号未注册");
            }
        }

        int rand = CodeUtil.rand(1000, 9999);
        CacheUtil.put(Key.as(CommonAttr.CACHE.VALIDATION_CODE, "SMS", param.get("type"), param.get("phone")), rand, TimeUnit.MINUTES, 5);

        // 发送短信
        smsService.sendCode(param.get("phone"), String.valueOf(rand));

        // 失效图形验证码
        CacheUtil.expire(Key.as(CommonAttr.CACHE.VALIDATION_CODE, index));
        return ResultBean.success("短信验证码已发送，请在5分钟内使用");
    }
}