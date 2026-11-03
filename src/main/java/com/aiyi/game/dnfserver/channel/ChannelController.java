package com.aiyi.game.dnfserver.channel;

import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.entity.AccountVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/channels")
public class ChannelController {
    private final ChannelService channels;
    private final AccountVODao accounts;
    public ChannelController(ChannelService channels, AccountVODao accounts) { this.channels = channels; this.accounts = accounts; }

    private void requireAdministrator(User user) {
        AccountVO account = user == null ? null : accounts.get(user.getId());
        // Process control and advertised addresses are reserved for super administrators.
        if (account == null || !account.isSuperAdmin()) throw new ChannelPermissionException();
    }

    @GetMapping public Map<String, Object> list(@RequestAttribute("LOGIN_USER") User user) throws Exception {
        requireAdministrator(user);
        return channels.list();
    }
    @GetMapping("/memory") public Map<String, Object> memory(@RequestAttribute("LOGIN_USER") User user) {
        requireAdministrator(user);
        return MemoryStats.read();
    }
    @PutMapping("/default-address") public Map<String, Object> saveDefault(@RequestAttribute("LOGIN_USER") User user, @RequestBody DefaultAddressSettings settings) throws Exception {
        requireAdministrator(user);
        return channels.saveDefaultAddress(settings);
    }
    @PutMapping("/{number}") public Map<String, Object> save(@RequestAttribute("LOGIN_USER") User user, @PathVariable int number, @RequestBody ChannelSettings settings) throws Exception {
        requireAdministrator(user);
        return channels.save(number, settings);
    }
    @PutMapping("/{number}/enabled") public Map<String, Object> setEnabled(@RequestAttribute("LOGIN_USER") User user, @PathVariable int number, @RequestBody ChannelEnabledRequest request) throws Exception {
        requireAdministrator(user);
        if (request == null || request.getEnabled() == null) throw new IllegalArgumentException("请选择频道状态");
        return channels.setEnabled(number, request.getEnabled());
    }
    @PostMapping("/{number}/restart") public Map<String, Object> restart(@RequestAttribute("LOGIN_USER") User user, @PathVariable int number) throws Exception {
        requireAdministrator(user);
        return channels.restart(number);
    }

    @ExceptionHandler(ChannelPermissionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> denied() { return Collections.singletonMap("message", "只有超管可以管理频道"); }

    private static class ChannelPermissionException extends RuntimeException { }
}
