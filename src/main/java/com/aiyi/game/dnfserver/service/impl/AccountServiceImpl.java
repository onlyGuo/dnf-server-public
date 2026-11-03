package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.LeftJoin;
import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.sql.where.C;
import com.aiyi.core.util.DateUtil;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.AdminTempPasswordDao;
import com.aiyi.game.dnfserver.dao.GameManagerAuthKeyDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.GameManagerAuthKey;
import com.aiyi.game.dnfserver.entity.LoginAccount;
import com.aiyi.game.dnfserver.service.AccountService;
import com.aiyi.game.dnfserver.security.RoleBootstrap;
import com.aiyi.game.dnfserver.security.OwnershipService;
import com.aiyi.game.dnfserver.security.GameKeyService;
import com.aiyi.game.dnfserver.utils.MD5;
import com.aiyi.game.dnfserver.utils.MinFieldUtil;
import com.aiyi.game.dnfserver.utils.RSATool;
import com.aiyi.game.dnfserver.utils.String2Hex;
import com.aiyi.game.dnfserver.utils.cache.CacheUtil;
import com.aiyi.game.dnfserver.utils.cache.Key;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * 用户业务操作类
 */
@Service
public class AccountServiceImpl implements AccountService {

    @Resource
    private AccountDao accountDao;

    @Resource
    private AccountVODao accountVODao;

    @Resource
    private GameManagerAuthKeyDao gameManagerAuthKeyDao;

    @Resource
    private AdminTempPasswordDao adminTempPasswordDao;

    @Resource
    private RoleBootstrap roles;

    @Resource
    private OwnershipService ownership;

    @Resource
    private GameKeyService gameKeys;

    @Override
    public String loginClient(AccountVO accountVO, boolean ok) {
        // 账号密码匹配用户
        String password = MD5.getMd5(accountVO.getPassword());
        AccountVO account = accountDao.getByAccountAndPswd(accountVO.getAccountname(), password);
        if (null == account){
            if (ok){
                throw new ValidationException("用户名或密码错误");
            }else{
                return "{\"message\":\"用户名或密码错误\"}";
            }
        }


        // 得到待加密的用户标识
//        String head = "0001FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
//        String end = "010101010101010101010101010101010101010101010101010101010101010155914510010403030101";
//        String data = String.format("%10x", account.getUid()) + end;


        // 得到待加密的用户标识
        String data = String.format("%08x0101010101010101010101010101010101010101010101010101010101010101559145100" +
                "10403030101", account.getUid());
        byte[] bs = String2Hex.hex2tobin(data);
        // 加密计算出用户授权Key
        byte[] resultByte = gameKeys.encryptForGame(bs);
        return Base64.getEncoder().encodeToString(resultByte);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(AccountVO accountVO, boolean isAdmin) {
        if (null == accountVO.getAccountname() || accountVO.getAccountname().trim().isEmpty()){
            throw new ValidationException("用户名不能为空");
        }
        if (null == accountVO.getPassword() || accountVO.getPassword().isEmpty()){
            throw new ValidationException("密码不能为空");
        }

//        Integer sms = CacheUtil.get(Key.as(CommonAttr.CACHE.VALIDATION_CODE, "SMS", "Register", accountVO.getAccountname()), Integer.class);
//        if (null == sms || !sms.toString().equals(accountVO.getSmsCode())){
//            throw new ValidationException("验证码不正确或已过期");
//        }

        String code = CacheUtil.get(Key.as(CommonAttr.CACHE.VALIDATION_CODE, accountVO.getValidationIndex()), String.class);
        if (null == code || !code.equals(accountVO.getValicode())){
            throw new ValidationException("验证码不正确或已过期");
        }

        // The role and account ownership are assigned by the server, never by JSON input.
        boolean firstAccount = roles.assignRegistrationRole(accountVO, isAdmin);
        accountVO.setUid(0);
        accountVO.setParentUid(0);

        Integer integer = accountDao.selectByAccount(accountVO.getAccountname());
        if (null != integer && integer > 0){
            throw new ValidationException("该用户名已存在, 请更换其他用户名");
        }

        if (!isAdmin && !firstAccount){
            if (StringUtils.isEmpty(accountVO.getRecommender())){
                accountVO.setRecommender("123123");
            }
        }
        if (isAdmin && !firstAccount){
            if (StringUtils.isEmpty(accountVO.getAuthCode())){
                throw new ValidationException("请填写授权码才能注册GM");
            }
            GameManagerAuthKey authKey = gameManagerAuthKeyDao.get(Method
                    .where(GameManagerAuthKey::getAuthCode, C.EQ, accountVO.getAuthCode()));
            if (null == authKey){
                throw new ValidationException("授权码不正确或已失效");
            }
            if (authKey.getUseCount() >= authKey.getMaxCount()){
                throw new ValidationException("授权码不正确或已失效");
            }

            authKey.setUseCount(authKey.getUseCount() + 1);
            gameManagerAuthKeyDao.update(authKey);
        }



        if (!firstAccount && StringUtils.hasText(accountVO.getRecommender())){
            AccountVO recommender = accountVODao.get(Method.where(AccountVO::getAccountname,
                    C.EQ, accountVO.getRecommender()));
            if (null == recommender){
                throw new ValidationException("推荐人账号不存在");
            }
            accountVO.setParentUid(recommender.getUid());
        }


        accountVO.setPassword(MD5.getMd5(accountVO.getPassword()));
        accountVODao.add(accountVO);
//        accountDao.insert(accountVO);

        accountDao.execute("INSERT INTO d_taiwan.member_info(m_id, user_id)VALUES('" + accountVO.getUid() + "', '" + accountVO.getUid() + "')");
        accountDao.execute("INSERT INTO d_taiwan.member_white_account(m_id)VALUES('" + accountVO.getUid() + "')");
        accountDao.execute("INSERT INTO taiwan_login.member_login(m_id)VALUES('" + accountVO.getUid() + "')");
        accountDao.execute("INSERT INTO taiwan_billing.cash_cera(account, cera,mod_tran, mod_date, reg_date)VALUES('" + accountVO.getUid() + "', 1000, 0, NOW(), NOW())");
        accountDao.execute("INSERT INTO taiwan_billing.cash_cera_point(account, cera_point,mod_date, reg_date)VALUES('" + accountVO.getUid() + "', 0, NOW(), NOW())");
        accountDao.execute("INSERT INTO taiwan_cain_2nd.member_avatar_coin(m_id)VALUES('" + accountVO.getUid() + "')");

        /*
         *# 充值10点券
         * update taiwan_billing.cash_cera set cera = cera + 10 where account='1';
         *
         * # 充值10代币券
         * update taiwan_billing.cash_cera_point SET cera_point = cera_point + 10 where account='1';
         *
         * # 解封
         * delete from d_taiwan.member_punish_info where m_id='2'
         *
         * # 禁封1天
         * insert into d_taiwan.member_punish_info (m_id,punish_type,occ_time,punish_value,apply_flag,start_time,end_time,reason) values ('uid','1',NOW(),'101','2',NOW(), date_sub(
         * 	NOW(),interval -1 day
         * ),'GM')
         *
         * # 发送邮件
         * insert into taiwan_cain_2nd.postal (occ_time,send_charac_name,receive_charac_no,amplify_option,amplify_value,seperate_upgrade,seal_flag,item_id,add_info,upgrade,gold,letter_id) values (” ＋ “'” ＋ time ＋ “'” ＋ “,'DNF admin','” ＋ 角色列表.取标题 (角色列表.现行选中项, 0) ＋ “','” ＋ 红字 ＋ “','” ＋ 编辑框8.内容（默认0） ＋ “','” ＋ 锻造等级 ＋ “','” ＋ 封装 ＋ “','” ＋ 物品代码 ＋ “','” ＋ 物品数量 ＋ “','” ＋强化等级 ＋ “','” ＋ 游戏金币 ＋ “','” ＋ 信件ID，对应letter表，但这个表是空的 ＋ “')
         *
         */

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(AccountVO accountVO) {
        register(accountVO, false);
    }

    @Override
    public ResultPage<AccountVO> list(String account, Boolean loginStatus,
                                      Date lastLoginDate, Date laseLoginDateEnd, int page,
                                      int pageSize) {
        List<AccountVO> list = null;
        int count = 0;
        long userId = ThreadUtil.getUserId();
        AccountVO accountVO = accountVODao.get(userId);
        if (laseLoginDateEnd != null){
            laseLoginDateEnd = new Date(laseLoginDateEnd.getTime() + DateUtil.DAY_TIME - 1);
        }
        if (accountVO.isSuperAdmin()){
            list =  accountDao.listAll(account, loginStatus,
                    lastLoginDate, laseLoginDateEnd, (page - 1) * pageSize, pageSize);
            count = accountDao.countAll(account, loginStatus, lastLoginDate, laseLoginDateEnd,
                    page, pageSize);
        }else{
            list = accountDao.list(account, loginStatus,
                    lastLoginDate, laseLoginDateEnd, accountVO.getUid(), (page - 1) * pageSize, pageSize);
            count = accountDao.count(account, loginStatus, lastLoginDate, laseLoginDateEnd, accountVO.getUid(),
                    page, pageSize);
        }
        list.forEach(item -> item.setPassword(null));
        return new ResultPage<>(count, page, pageSize, list);
    }

    @Override
    public void recharge(long uid, int face) {
        ownership.requireAccount(uid);
        accountVODao.execute("update taiwan_billing.cash_cera set " +
                "cera = cera + ? where account=?", face, uid);

    }

    @Override
    public AccountVO info(long uid) {
        AccountVO accountVO = ownership.requireAccount(uid);
        List<AccountVO> list = accountVODao.list("SELECT cera FROM taiwan_billing.cash_cera " +
                "WHERE account=?", uid);
        if (!list.isEmpty()) accountVO.setCera(list.get(0).getCera());
        accountVO.setPassword(null);
        return accountVO;
    }

    public static void main(String[] args) {
        String data = String.format("%08x0101010101010101010101010101010101010101010101010101010101010101"
                , 18000000);
        System.out.println(data);
        byte[] hexed = String2Hex.hex2tobin(data);
        // 加密计算出用户授权Key

        String token = null;
        try {
            String privateKey = new String(MinFieldUtil.readResource("private.key")).replace("\r", "")
                    .replace("\n", "");
            byte[] resultByte = RSATool.encryptByPrivateKey(hexed, privateKey);
            token = Base64.getEncoder().encodeToString(resultByte);
        }catch (Exception e){
            byte[] resultByte = RSATool.encryptByPrivateKey(hexed, ( "-----BEGIN RSA PRIVATE KEY-----\n" +
                    new String(MinFieldUtil.readResource("private.key")) + "\n-----END RSA PRIVATE KEY-----").getBytes());
            token = Base64.getEncoder().encodeToString(resultByte);
        }

        System.out.println(token);
    }
}
