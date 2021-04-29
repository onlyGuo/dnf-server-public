package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.service.CharacService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 * 角色相关业务
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 14:25
 */
@Service
public class CharacServiceImpl implements CharacService {

    @Resource
    private CharacInfoDao characInfoDao;
    @Resource
    private AccountDao accountDao;
    @Resource
    private AccountVODao accountVODao;

    @Override
    public ResultPage<CharacInfo> list(Integer levMin, Integer levMax, Integer job,
                                       String name, String account, Integer page, Integer pageSize) {
        if (null == levMin) levMin = 1;
        if (null == levMax) levMax = 999;
        WherePrams where = Method.where(CharacInfo::getLev, C.DE, levMin).and(CharacInfo::getLev, C.XE, levMax);
        if (null != job){
            where.and(CharacInfo::getJob, C.EQ, job);
        }

        if (!StringUtils.isEmpty(name)){
            where.and(CharacInfo::getCharacName, C.LIKE, ChinaseUtil.toTraditional(name));
        }
        if (!StringUtils.isEmpty(account)){
            List<Integer> ids = accountVODao.list(Method.where(AccountVO::getAccountname, C.LIKE, account))
                    .stream().map(AccountVO::getUid).collect(Collectors.toList());
            if (ids.isEmpty()){
                where.and(CharacInfo::getMid, C.EQ, null);
            }else{
                where.and(CharacInfo::getMid, C.IN, ids);
            }
        }
        return characInfoDao.list(where, page, pageSize);
    }

    @Override
    public List<CharacInfo> list(String account) {
        AccountVO accountVO = accountVODao.get(Method
                .where(AccountVO::getAccountname, C.EQ, account));
        if (null == accountVO){
            return new LinkedList<>();
        }

        List<CharacInfo> list = characInfoDao.list(Method
                .where(CharacInfo::getMid, C.EQ, accountVO.getUid()));

        for (CharacInfo characInfo: list){
            ChinaseUtil.toSimple(characInfo);
        }
        return list;
    }
}
