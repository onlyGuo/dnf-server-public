package com.aiyi.game.dnfserver.task;

import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
@ConditionalOnProperty(name = "dnf.member.reset-enabled", havingValue = "true", matchIfMissing = true)
public class MemberTask {

    @Resource
    private AccountVODao accountVODao;

    @Value("${dnf.channels.data-dir:/data}")
    private String dataDir;

    @Value("${DNF_MEMBER_RESET_ENABLED:}")
    private String explicitReset;

    /**
     * 角色创建限制, 每3秒重置一次
     */
    @Scheduled(cron = "0/3 * * * * ?")
    public void roleCreateLimitReset(){
        // The local IDE simulator shares the real database, whose running Web
        // already performs this task. A deliberate environment override wins.
        if (explicitReset.trim().isEmpty() && Files.isRegularFile(Paths.get(dataDir, ".dnf-local-simulator"))) return;
        // 每天凌晨重置角色创建限制
        /**
         * 18000000,1,2025-10-21 20:23:18
         * 18000002,0,2025-09-24 22:53:28
         * 18000008,0,2025-09-29 21:24:44
         * 18000010,0,2025-09-29 21:29:00
         * 18000011,0,2025-10-06 14:52:46
         * 18000009,1,2025-10-21 20:34:25
         */
        // update limit_create_character set count='0'
        accountVODao.execute("TRUNCATE TABLE `d_taiwan`.`limit_create_character`");
    }
}
