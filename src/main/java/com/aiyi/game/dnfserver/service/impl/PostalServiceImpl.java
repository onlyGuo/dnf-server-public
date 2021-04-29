package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.Sort;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.enums.OrderBy;
import com.aiyi.core.sql.where.C;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.service.PostalService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;

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

    @Override
    public ResultPage<Postal> list(String account, Date start,
                                   Date end, Integer page,
                                   Integer pageSize) {
        WherePrams where = Method.createDefault();
        if (!StringUtils.isEmpty(account)){
            Integer id = accountDao.selectByAccount(account);
            if (null != id){
                where.and(Postal::getReceiveCharacNo, C.EQ, id);
            }
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
    public void sendMail(Postal postal) {
        postal.setSendCharacName("DNF Manager");
        postalDao.add(postal);
    }
}
