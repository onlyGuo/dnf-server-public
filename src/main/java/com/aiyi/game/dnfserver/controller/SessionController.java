package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.conf.GmAllowed;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.security.OwnershipService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@GmAllowed
@RequestMapping("api/v1/session")
public class SessionController {
    private final OwnershipService ownership;
    public SessionController(OwnershipService ownership) { this.ownership = ownership; }
    @GetMapping public Map<String, Object> current() {
        AccountVO user = ownership.current();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uid", user.getUid());
        result.put("account", user.getAccountname());
        result.put("admin", user.getAdmin());
        return result;
    }
}
