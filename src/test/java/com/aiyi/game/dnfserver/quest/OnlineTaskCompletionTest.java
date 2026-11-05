package com.aiyi.game.dnfserver.quest;

import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.security.OwnershipService;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.service.impl.CharacServiceImpl;
import com.xiaoyouma.dnf.parser.pvf.model.Pvf;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class OnlineTaskCompletionTest {
    @Test public void onlineCompletionUsesLiveSnapshotAndNeverUpdatesOfflineProgress() {
        OnlineQuestBridge bridge = mock(OnlineQuestBridge.class);
        CharacInfoDao dao = mock(CharacInfoDao.class);
        PostalService postal = mock(PostalService.class);
        CharacInfo character = new CharacInfo();
        character.setCharacNo(1);
        Map<String, Object> snapshot = snapshot();
        when(bridge.snapshot(character)).thenReturn(snapshot);
        CharacServiceImpl service = service(bridge, dao, postal, character);
        service.overTasks(1L);
        verify(bridge).complete(character, snapshot, Collections.singletonList(101));
        verifyZeroInteractions(dao, postal);
    }

    @Test public void changedGameStateDoesNotFallBackToDatabaseWrites() {
        OnlineQuestBridge bridge = mock(OnlineQuestBridge.class);
        CharacInfoDao dao = mock(CharacInfoDao.class);
        PostalService postal = mock(PostalService.class);
        CharacInfo character = new CharacInfo();
        Map<String, Object> snapshot = snapshot();
        when(bridge.snapshot(character)).thenReturn(snapshot);
        doThrow(new IllegalArgumentException("changed")).when(bridge)
                .complete(character, snapshot, Collections.singletonList(101));
        try { service(bridge, dao, postal, character).overTasks(1L); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("changed", expected.getMessage()); }
        verifyZeroInteractions(dao, postal);
    }

    private CharacServiceImpl service(OnlineQuestBridge bridge, CharacInfoDao dao,
                                      PostalService postal, CharacInfo character) {
        OwnershipService ownership = mock(OwnershipService.class);
        when(ownership.requireCharacter(1L)).thenReturn(character);
        PvfManager manager = mock(PvfManager.class);
        Pvf pvf = mock(Pvf.class);
        when(manager.getPvf()).thenReturn(pvf);
        when(pvf.getScript("n_quest/quest.lst")).thenReturn(new JSONObject("{\"101\":\"test.que\"}"));
        when(pvf.getScript("n_quest/test.que")).thenReturn(new JSONObject("{\"[type]\":[\"[hunt monster]\"]}"));
        CharacServiceImpl service = new CharacServiceImpl();
        ReflectionTestUtils.setField(service, "ownership", ownership);
        ReflectionTestUtils.setField(service, "onlineQuests", bridge);
        ReflectionTestUtils.setField(service, "characInfoDao", dao);
        ReflectionTestUtils.setField(service, "postalService", postal);
        ReflectionTestUtils.setField(service, "pvfManager", manager);
        return service;
    }

    private Map<String, Object> snapshot() {
        StringBuilder response = new StringBuilder("snapshot");
        for (int slot = 0; slot < 20; slot++) response.append(slot == 0 ? " 101 5" : " 0 0");
        return OnlineQuestBridge.parseSnapshot(response.toString());
    }
}
