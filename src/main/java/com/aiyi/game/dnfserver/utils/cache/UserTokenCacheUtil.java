package com.aiyi.game.dnfserver.utils.cache;

import com.aiyi.core.beans.Method;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.dao.User;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * @Author: 郭胜凯
 * @Date: 2020/10/9 17:22
 * @Email 719348277@qq.com
 * @Description:
 */
public class UserTokenCacheUtil {

    /**
     * 缓存时间(单位: 小时)
     */
    private static final int expire = 2;


    /**
     * 缓存一个token和用户的对应关系
     * @param token
     *      登录令牌
     * @param user
     *      用户对象
     */
    public static void putUserCache(String token, User user){
        clear(user.getId());
        // 缓存2小时
        CacheUtil.put(Key.as(CommonAttr.CACHE.LOGIN_KEY, token), user, TimeUnit.HOURS, expire);
        CacheUtil.put(Key.as(CommonAttr.CACHE.USER_ID_TOKEN, String.valueOf(user.getId())), token, TimeUnit.HOURS, expire);
    }

    /**
     * 清除用户的登录缓存
     * @param userId
     *      对应的用户ID
     */
    public static void clear(int userId) {
        String token = CacheUtil.get(Key.as(CommonAttr.CACHE.USER_ID_TOKEN, String.valueOf(userId)), String.class);
        if (null != token){
            CacheUtil.expire(Key.as(CommonAttr.CACHE.USER_ID_TOKEN, String.valueOf(userId)));
            CacheUtil.expire(Key.as(CommonAttr.CACHE.LOGIN_KEY, token));
        }
    }

    /**
     * 更新缓存中的用户(token对应的用户对象)
     * @param user
     *      新的用户对象
     */
    public static void updateCacheUser(User user) {
        String token = CacheUtil.get(Key.as(CommonAttr.CACHE.USER_ID_TOKEN, String.valueOf(user.getId())), String.class);
        if (null != token){
            CacheUtil.put(Key.as(CommonAttr.CACHE.LOGIN_KEY, token), user, TimeUnit.HOURS, expire);
            CacheUtil.put(Key.as(CommonAttr.CACHE.USER_ID_TOKEN, String.valueOf(user.getId())), token, TimeUnit.HOURS, expire);
        }
    }

    /**
     * 通过Token获得用户信息
     * @param token
     *      token
     * @return
     */
    public static User getUser(String token){
        if (StringUtils.isEmpty(token)){
            return null;
        }
        return CacheUtil.get(Key.as(CommonAttr.CACHE.LOGIN_KEY, token), User.class);
    }
}