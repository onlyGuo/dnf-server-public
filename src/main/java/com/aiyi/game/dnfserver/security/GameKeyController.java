package com.aiyi.game.dnfserver.security;

import com.aiyi.game.dnfserver.conf.ForbiddenException;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.entity.AccountVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/keys")
public class GameKeyController {
    private final GameKeyService keys;
    private final AccountVODao accounts;

    public GameKeyController(GameKeyService keys, AccountVODao accounts) {
        this.keys = keys;
        this.accounts = accounts;
    }

    private void requireSuperAdmin(User user) {
        AccountVO account = user == null ? null : accounts.get(user.getId());
        if (account == null || !account.isSuperAdmin()) throw new ForbiddenException("只有超管可以管理游戏密钥");
    }

    @GetMapping
    public Map<String, Object> status(@RequestAttribute("LOGIN_USER") User user) {
        requireSuperAdmin(user);
        return keys.status();
    }

    @PostMapping("upload")
    public Map<String, Object> upload(@RequestAttribute("LOGIN_USER") User user,
                                      @RequestParam("privateKey") MultipartFile privateKey,
                                      @RequestParam("publicKey") MultipartFile publicKey) throws Exception {
        requireSuperAdmin(user);
        return keys.upload(privateKey, publicKey);
    }

    @PostMapping("generate")
    public Map<String, Object> generate(@RequestAttribute("LOGIN_USER") User user) throws Exception {
        requireSuperAdmin(user);
        return keys.generate();
    }
}
