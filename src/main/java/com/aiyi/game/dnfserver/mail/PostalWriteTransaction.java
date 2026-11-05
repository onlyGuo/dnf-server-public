package com.aiyi.game.dnfserver.mail;

import com.aiyi.game.dnfserver.dao.PostalDao;
import com.aiyi.game.dnfserver.entity.Postal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/** The game mail and its notification request must commit together. */
@Service
public class PostalWriteTransaction {
    @Resource private PostalDao postalDao;
    @Resource private OnlineMailNotificationService notifications;

    @Transactional(rollbackFor = Exception.class)
    public String write(Postal postal, boolean notify) {
        postalDao.add(postal);
        if (!notify) return null;
        return notifications.enqueue(Long.parseLong(postal.getReceiveCharacNo()));
    }
}
