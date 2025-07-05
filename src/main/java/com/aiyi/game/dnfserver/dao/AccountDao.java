package com.aiyi.game.dnfserver.dao;

import com.aiyi.game.dnfserver.entity.AccountVO;
import org.apache.ibatis.annotations.*;

import java.util.Date;
import java.util.List;

/**
 * 用户表数据操作接口
 * @author gsk
 */
@Mapper
public interface AccountDao {

    /**
     * 通过账号和密码定位某个用户
     * @param account
     *          账号
     * @param password
     *          密码
     * @return AccountVO
     *          匹配账号密码的用户实体
     */
    AccountVO getByAccountAndPswd(@Param("account") String account, @Param("password") String password);

    /**
     * 向数据库中插入一条账号记录
     * @param accountVO
     *          要插入的账号实体
     * @return int
     *          受更改的记录数
     */
    @Insert("INSERT INTO\n" +
            "            d_taiwan.accounts (accountname, password, qq, vip)\n" +
            "        VALUES\n" +
            "            (#{accountname}, #{password}, #{qq}, '')")
    @Options(keyProperty = "uid", useGeneratedKeys = true)
    int insert(AccountVO accountVO);

    /**
     * 根据账号查找
     * @param name
     * @return
     */
    Integer selectByAccount(String name);

    @Update("${sql}")
    void execute(String sql);

    @Select("<script>" +
            "    SELECT\n" +
            "        accounts.UID AS uid,\n" +
            "        accounts.accountname AS accountname,\n" +
            "        accounts.password AS password,\n" +
            "        accounts.qq AS qq,\n" +
            "        accounts.vip AS vip,\n" +
            "        login_account.login_ip AS loginIp,\n" +
            "        login_account.m_channel_no AS channelNo,\n" +
            "        login_account.last_login_date AS lastLoginDate,\n" +
            "        login_account.login_status AS loginStatus\n" +
            "    FROM\n" +
            "        d_taiwan.`accounts` AS accounts\n" +
            "    LEFT JOIN\n" +
            "        taiwan_login.`login_account_3` AS login_account\n" +
            "            ON  login_account.m_id = d_taiwan.`accounts`.UID\n" +
            "    WHERE\n" +
            "        parent_uid=#{parentUid}\n" +
            "        <if test=\"null != account and '' != account\">AND accounts.accountname LIKE '%${account}%'</if>\n" +
            "        <if test=\"null != loginStatus\">AND login_account.login_status = #{loginStatus}</if>\n" +
            "        <if test=\"null != lastLoginDate\">AND login_account.last_login_date &lt;= #{lastLoginDate}</if>\n" +
            "    LIMIT #{page} , #{pageSize}" +
            "</script>")
    List<AccountVO> list(@Param("account") String account,
                         @Param("loginStatus") Boolean loginStatus,
                         @Param("lastLoginDate")Date lastLoginDate,
                         @Param("parentUid") int parentUid,
                         @Param("page") Integer page,
                         @Param("pageSize")Integer pageSize);

    @Select("<script>" +
            "    SELECT\n" +
            "        COUNT(*) AS COUNT\n" +
            "    FROM\n" +
            "        d_taiwan.`accounts` AS accounts\n" +
            "    LEFT JOIN\n" +
            "        taiwan_login.`login_account_3` AS login_account\n" +
            "            ON  login_account.m_id = d_taiwan.`accounts`.UID\n" +
            "    WHERE\n" +
            "        parent_uid=#{parentUid}\n" +
            "        <if test=\"null != account and '' != account\">AND accounts.accountname LIKE '%${account}%'</if>\n" +
            "        <if test=\"null != loginStatus\">AND login_account.login_status = #{loginStatus}</if>\n" +
            "        <if test=\"null != lastLoginDate\">AND login_account.last_login_date &lt;= #{lastLoginDate}</if>\n" +
            "</script>")
    int count(@Param("account") String account,
               @Param("loginStatus") Boolean loginStatus,
               @Param("lastLoginDate")Date lastLoginDate,
               @Param("parentUid") int parentUid,
               @Param("page") Integer page,
               @Param("pageSize")Integer pageSize);

    @Select("<script>" +
            "    SELECT\n" +
            "        accounts.UID AS uid,\n" +
            "        accounts.accountname AS accountname,\n" +
            "        accounts.password AS password,\n" +
            "        accounts.qq AS qq,\n" +
            "        accounts.vip AS vip,\n" +
            "        login_account.login_ip AS loginIp,\n" +
            "        login_account.m_channel_no AS channelNo,\n" +
            "        login_account.last_login_date AS lastLoginDate,\n" +
            "        login_account.login_status AS loginStatus\n" +
            "    FROM\n" +
            "        d_taiwan.`accounts` AS accounts\n" +
            "    LEFT JOIN\n" +
            "        taiwan_login.`login_account_3` AS login_account\n" +
            "            ON  login_account.m_id = d_taiwan.`accounts`.UID\n" +
            "    WHERE\n" +
            "        1=1\n" +
            "        <if test=\"null != account and '' != account\">AND accounts.accountname LIKE '%${account}%'</if>\n" +
            "        <if test=\"null != loginStatus\">AND login_account.login_status = #{loginStatus}</if>\n" +
            "        <if test=\"null != lastLoginDate\">AND login_account.last_login_date &lt;= #{lastLoginDate}</if>\n" +
            "    LIMIT #{page} , #{pageSize}" +
            "</script>")
    List<AccountVO> listAll(@Param("account") String account,
                         @Param("loginStatus") Boolean loginStatus,
                         @Param("lastLoginDate")Date lastLoginDate,
                         @Param("page") Integer page,
                         @Param("pageSize")Integer pageSize);

    @Select("<script>" +
            "    SELECT\n" +
            "        COUNT(*) AS COUNT\n" +
            "    FROM\n" +
            "        d_taiwan.`accounts` AS accounts\n" +
            "    LEFT JOIN\n" +
            "        taiwan_login.`login_account_3` AS login_account\n" +
            "            ON  login_account.m_id = d_taiwan.`accounts`.UID\n" +
            "    WHERE\n" +
            "        1=1\n" +
            "        <if test=\"null != account and '' != account\">AND accounts.accountname LIKE '%${account}%'</if>\n" +
            "        <if test=\"null != loginStatus\">AND login_account.login_status = #{loginStatus}</if>\n" +
            "        <if test=\"null != lastLoginDate\">AND login_account.last_login_date &lt;= #{lastLoginDate}</if>\n" +
            "</script>")
    int countAll(@Param("account") String account,
              @Param("loginStatus") Boolean loginStatus,
              @Param("lastLoginDate")Date lastLoginDate,
              @Param("page") Integer page,
              @Param("pageSize")Integer pageSize);

}
