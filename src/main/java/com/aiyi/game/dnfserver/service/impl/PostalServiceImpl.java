package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.Sort;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.enums.OrderBy;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.security.OwnershipService;
import com.aiyi.game.dnfserver.mail.OnlineMailNotificationService;
import com.aiyi.game.dnfserver.mail.PostalWriteTransaction;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 16:37
 */
@Service
public class PostalServiceImpl implements PostalService {

    @Resource
    private PostalDao postalDao;

    @Resource
    private AccountDao accountDao;

    @Resource
    private AccountVODao accounts;

    @Resource
    private CharacInfoDao characters;

    @Resource
    private OwnershipService ownership;

    @Resource
    private OnlineMailNotificationService notifications;

    @Resource
    private PostalWriteTransaction postalWriter;

    @Override
    public ResultPage<Postal> list(String account, Date start,
                                   Date end, Integer page,
                                   Integer pageSize) {
        WherePrams where = Method.createDefault();
        AccountVO current = ownership.current();
        List<Long> accountIds = null;
        if (!StringUtils.isEmpty(account)){
            Integer id = accountDao.selectByAccount(account);
            if (id == null) return new ResultPage<>(0, page, pageSize, new ArrayList<>());
            ownership.requireAccount(id);
            accountIds = java.util.Collections.singletonList(id.longValue());
        } else if (!current.isSuperAdmin()) {
            accountIds = accounts.list(Method.where(AccountVO::getParentUid, C.EQ, current.getUid()))
                    .stream().map(AccountVO::getUid).collect(Collectors.toList());
        }
        if (accountIds != null) {
            if (accountIds.isEmpty()) return new ResultPage<>(0, page, pageSize, new ArrayList<>());
            List<Integer> ids = characters.list(Method.where(CharacInfo::getMid, C.IN, accountIds))
                    .stream().map(CharacInfo::getCharacNo).collect(Collectors.toList());
            if (ids.isEmpty()) return new ResultPage<>(0, page, pageSize, new ArrayList<>());
            where.and(Postal::getReceiveCharacNo, C.IN, ids);
        }
        if (null != start){
            where.and(Postal::getOccTime, C.DE, start);
        }
        if (null != end){
            where.and(Postal::getOccTime, C.XE, end);
        }
        where.orderBy(Sort.of(Postal::getPostalId, OrderBy.DESC));
        return postalDao.list(where, page, pageSize);
    }

    @Override
    public String sendMail(Postal postal) {
        postal.setSendCharacName("DNF Manager");
        boolean notify = notifications.ensureQueue();
        return postalWriter.write(postal, notify);
    }
}
