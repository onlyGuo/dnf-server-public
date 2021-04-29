package com.aiyi.game.dnfserver.conf;

import java.lang.annotation.*;

/**
 * @Author: 郭胜凯
 * @Date: 2019-07-10 10:48
 * @Email 719348277@qq.com
 * @Description: Controller方法中打入该注解, 表示不进行登录认证
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoLogin {

}