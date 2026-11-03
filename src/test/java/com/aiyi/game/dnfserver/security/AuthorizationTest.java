package com.aiyi.game.dnfserver.security;

import com.aiyi.core.exception.AccessOAuthException;
import com.aiyi.game.dnfserver.conf.ForbiddenException;
import com.aiyi.game.dnfserver.conf.RequestThreadFilterConf;
import com.aiyi.game.dnfserver.controller.SessionController;
import com.aiyi.game.dnfserver.controller.ClientAccountController;
import com.aiyi.game.dnfserver.database.DatabaseController;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AuthorizationTest {
    @Test public void roleCheckedOnEveryRequestAndClientLoginRemainsPublic() throws Exception {
        AccountVODao accounts = mock(AccountVODao.class);
        AccountVO user = new AccountVO(); user.setUid(98765); user.setAdmin(1);
        when(accounts.get(98765L)).thenReturn(user);
        RequestThreadFilterConf filter = new RequestThreadFilterConf();
        ReflectionTestUtils.setField(filter, "accounts", accounts);
        User session = new User(); session.setId(98765); UserTokenCacheUtil.putUserCache("role-test", session);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer role-test");
        HandlerMethod gmEndpoint = new HandlerMethod(new SessionController(mock(OwnershipService.class)), "current");
        HandlerMethod superEndpoint = new HandlerMethod(new DatabaseController(null), "schemas");
        assertTrue(filter.preHandle(request, new MockHttpServletResponse(), gmEndpoint));
        try { filter.preHandle(request, new MockHttpServletResponse(), superEndpoint); fail(); } catch (ForbiddenException expected) { }
        user.setAdmin(2);
        assertTrue(filter.preHandle(request, new MockHttpServletResponse(), superEndpoint));
        user.setAdmin(0); user.setParentUid(0);
        try { filter.preHandle(request, new MockHttpServletResponse(), gmEndpoint); fail(); } catch (AccessOAuthException expected) { }
        HandlerMethod client = new HandlerMethod(new ClientAccountController(), "loginClieng2", AccountVO.class);
        assertTrue(filter.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), client));
        UserTokenCacheUtil.clear(98765);
    }
}
