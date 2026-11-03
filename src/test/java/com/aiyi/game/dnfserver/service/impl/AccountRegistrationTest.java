package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.beans.WherePrams;
import com.aiyi.game.dnfserver.conf.CommonAttr;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.GameManagerAuthKeyDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.GameManagerAuthKey;
import com.aiyi.game.dnfserver.utils.cache.CacheUtil;
import com.aiyi.game.dnfserver.utils.cache.Key;
import com.aiyi.game.dnfserver.security.RoleBootstrap;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AccountRegistrationTest {
    private final AccountDao accountDao = mock(AccountDao.class);
    private final AccountVODao accountVODao = mock(AccountVODao.class);
    private final GameManagerAuthKeyDao gameManagerAuthKeyDao = mock(GameManagerAuthKeyDao.class);
    private final RoleBootstrap roles = mock(RoleBootstrap.class);

    private AccountServiceImpl service() {
        AccountServiceImpl service = new AccountServiceImpl();
        ReflectionTestUtils.setField(service, "accountDao", accountDao);
        ReflectionTestUtils.setField(service, "accountVODao", accountVODao);
        ReflectionTestUtils.setField(service, "gameManagerAuthKeyDao", gameManagerAuthKeyDao);
        ReflectionTestUtils.setField(service, "roles", roles);
        return service;
    }

    private AccountVO request(String index) {
        CacheUtil.put(Key.as(CommonAttr.CACHE.VALIDATION_CODE, index), "answer", TimeUnit.MINUTES, 1);
        AccountVO account = new AccountVO();
        account.setAccountname("qa-player");
        account.setPassword("qa-password");
        account.setValidationIndex(index);
        account.setValicode("answer");
        account.setParentUid(123);
        account.setVip("unchanged");
        account.setAdmin(2);
        return account;
    }

    @Test public void playerRegistrationForcesRoleAndUsesServerResolvedParent() {
        AccountVO account = request("qa-player-registration");
        AccountVO recommender = new AccountVO(); recommender.setUid(42);
        when(accountVODao.get(any(WherePrams.class))).thenReturn(recommender);
        when(roles.assignRegistrationRole(account, false)).thenAnswer(invocation -> { account.setAdmin(0); return false; });
        service().register(account, false);
        verify(accountVODao).add(account);
        assertEquals(0, account.getAdmin());
        assertEquals(42, account.getParentUid());
        assertEquals("unchanged", account.getVip());
    }

    @Test public void gmRegistrationKeepsItsExistingBehavior() {
        GameManagerAuthKey key = new GameManagerAuthKey();
        key.setMaxCount(1);
        key.setUseCount(0);
        when(gameManagerAuthKeyDao.get(any(WherePrams.class))).thenReturn(key);
        AccountVO account = request("qa-gm-registration");
        account.setAuthCode("valid-code");
        when(roles.assignRegistrationRole(account, true)).thenAnswer(invocation -> { account.setAdmin(1); return false; });
        service().register(account, true);
        verify(accountVODao).add(account);
        assertEquals(1, account.getAdmin());
    }

    @Test public void firstRegistrationDoesNotRequireAnExistingRecommender() {
        AccountVO account = request("qa-first-registration");
        when(roles.assignRegistrationRole(account, false)).thenAnswer(invocation -> { account.setAdmin(2); return true; });
        service().register(account, false);
        verify(accountVODao).add(account);
        assertEquals(2, account.getAdmin());
        assertEquals(0, account.getParentUid());
    }
}
