package com.aiyi.game.dnfserver.task;

import com.aiyi.game.dnfserver.dao.AccountVODao;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.*;
import static org.junit.Assert.assertTrue;

public class MemberTaskTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void ideSimulationSkipsCleanupUnlessExplicitlyEnabled() throws Exception {
        Path data = temp.newFolder("dnf").toPath();
        Files.createFile(data.resolve(".dnf-local-simulator"));
        AccountVODao dao = mock(AccountVODao.class);
        MemberTask task = new MemberTask();
        ReflectionTestUtils.setField(task, "accountVODao", dao);
        ReflectionTestUtils.setField(task, "dataDir", data.toString());
        ReflectionTestUtils.setField(task, "explicitReset", "");
        task.roleCreateLimitReset();
        verify(dao, never()).execute(anyString());
        ReflectionTestUtils.setField(task, "explicitReset", "true");
        task.roleCreateLimitReset();
        verify(dao).execute("TRUNCATE TABLE `d_taiwan`.`limit_create_character`");
    }

    @Test public void falseEnvironmentSettingPreventsTaskBeanCreation() {
        new ApplicationContextRunner()
                .withUserConfiguration(MemberTask.class)
                .withPropertyValues("DNF_MEMBER_RESET_ENABLED=false",
                        "dnf.member.reset-enabled=${DNF_MEMBER_RESET_ENABLED:true}")
                .run(context -> assertTrue(context.getBeansOfType(MemberTask.class).isEmpty()));
    }
}
