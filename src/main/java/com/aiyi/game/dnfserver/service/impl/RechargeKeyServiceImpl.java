package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.dao.RechargeKeyDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.entity.RechargeKey;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.service.RechargeKeyService;
import com.aiyi.game.dnfserver.utils.cache.CacheUtil;
import com.aiyi.game.dnfserver.utils.cache.Key;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class RechargeKeyServiceImpl implements RechargeKeyService {

    @Resource
    private RechargeKeyDao rechargeKeyDao;

    @Resource
    private AccountVODao accountVODao;

    @Resource
    private CharacInfoDao characInfoDao;

    @Resource
    private PostalService postalService;

    @Override
    public ResultPage<RechargeKey> list(Integer type, Integer status,
                                        int page, int pageSize,
                                        String content) {
        WherePrams where = Method.createDefault();
        if (null != type){
            where.and(RechargeKey::getType, C.EQ, type);
        }
        if (null != status){
            where.and(RechargeKey::getStatus, C.EQ, status);
        }
        if (!StringUtils.isEmpty(content)){
            where.and(RechargeKey::getContent, C.LIKE, content);
        }
        ResultPage<RechargeKey> list = rechargeKeyDao.list(where,
                page, pageSize);
        for (RechargeKey key: list.getList()){
            if (!StringUtils.isEmpty(key.getFaceName())){
                key.setFaceName(new String(
                    Base64.getDecoder().decode(key.getFaceName()),
                    StandardCharsets.UTF_8
                ));
            }
        }
        return list;
    }

    @Override
    public void create(RechargeKey rechargeKey) {
        if (rechargeKey.getType() == 0){
            if (rechargeKey.getFace() < 1){
                throw new ValidationException("面值无效");
            }
        }else{
            if (rechargeKey.getFace() < 0){
                throw new ValidationException("选择的物品无效");
            }
        }

        if (rechargeKey.getCount() < 1){
            throw new ValidationException("卡密数量不能小于1");
        }

        for (int i = 0; i < rechargeKey.getCount(); i ++){
            RechargeKey key = new RechargeKey();
            key.setContent(UUID.randomUUID().toString().toUpperCase());
            key.setCreateTime(new Date());
            key.setFace(rechargeKey.getFace());
            key.setFaceName(Base64.getEncoder().encodeToString(
                    rechargeKey.getFaceName().
                            getBytes(StandardCharsets.UTF_8)));
            key.setType(rechargeKey.getType());
            rechargeKeyDao.add(key);
        }
    }

    @Override
    public void use(RechargeKey rechargeKey) {
        CacheUtil.lock(Key.as("RECHARGE", rechargeKey.getContent()),
        () -> {
            String valicode = rechargeKey.getValicode();
            String cacheValiCode = CacheUtil.get(Key.as(
                    CommonAttr.CACHE.VALIDATION_CODE,
                    rechargeKey.getValiId()
            ), String.class);
            if (null == valicode || !valicode.equals(cacheValiCode)){
                throw new ValidationException("验证码不正确或已过期");
            }
            RechargeKey key = rechargeKeyDao.get(Method
                    .where(RechargeKey::getContent, C.EQ,
                            rechargeKey.getContent()));
            if (null == key){
                throw new ValidationException("卡密不存在");
            }
            if (key.getStatus() == 1){
                throw new ValidationException("该卡密已被使用");
            }

            AccountVO accountVO = accountVODao.get(Method
                    .where(AccountVO::getAccountname,
                            C.EQ, rechargeKey.getUseAccount()));
            if (key.getType() == 0){
                // 充值点券
                accountVODao.execute("update taiwan_billing." +
                        "cash_cera set " +
                        "cera = cera + ? where account=?",
                        key.getFace(), accountVO.getUid());
            }else{
                // 充值装备
                CharacInfo info = characInfoDao.get(rechargeKey
                        .getCharach());
                Postal postal = new Postal();
                postal.setAddInfo(1);
                postal.setItemId(rechargeKey.getFace());
                postal.setOccTime(new Date());
                postal.setSendCharacName("DNF Manager");
                postal.setReceiveCharacNo(info.getCharacNo() + "");
                postalService.sendMail(postal);
            }

            // 销毁验证码
            CacheUtil.expire(Key.as(
                    CommonAttr.CACHE.VALIDATION_CODE,
                    rechargeKey.getValiId()
            ));

            // 置卡密状态
            key.setStatus(1);
            key.setUseUid(accountVO.getUid());
            key.setUseAccount(accountVO.getAccountname());
            key.setUseTime(new Date());
            rechargeKeyDao.update(key);
            return null;
        });
    }
}
