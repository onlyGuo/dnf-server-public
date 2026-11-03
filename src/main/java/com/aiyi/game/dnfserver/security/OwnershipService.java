package com.aiyi.game.dnfserver.security;

import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.conf.ForbiddenException;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import org.springframework.stereotype.Service;

@Service
public class OwnershipService {
    private final AccountVODao accounts;
    private final CharacInfoDao characters;

    public OwnershipService(AccountVODao accounts, CharacInfoDao characters) {
        this.accounts = accounts;
        this.characters = characters;
    }

    public AccountVO current() {
        Long id = ThreadUtil.getUserId();
        AccountVO user = id == null ? null : accounts.get(id);
        if (user == null || (user.getAdmin() != 1 && user.getAdmin() != 2))
            throw new ForbiddenException("没有后台管理权限");
        return user;
    }

    public AccountVO requireAccount(long id) {
        AccountVO current = current();
        AccountVO target = accounts.get(id);
        if (target == null) throw new IllegalArgumentException("账号不存在");
        if (!current.isSuperAdmin() && target.getParentUid() != current.getUid())
            throw new ForbiddenException("只能管理自己的下级账号");
        return target;
    }

    public CharacInfo requireCharacter(long id) {
        if (id <= 0 || id > Integer.MAX_VALUE) throw new IllegalArgumentException("角色不存在");
        CharacInfo character = characters.get((int) id);
        if (character == null) throw new IllegalArgumentException("角色不存在");
        requireAccount(character.getMid());
        return character;
    }
}
