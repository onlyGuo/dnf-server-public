package com.aiyi.game.dnfserver.inventory;

import com.aiyi.game.dnfserver.conf.GmAllowed;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.Map;

@RestController
@GmAllowed
@RequestMapping("api/v1/charac/{characterId}/inventory")
public class InventoryController {
    private final InventoryService inventory;
    public InventoryController(InventoryService inventory) { this.inventory = inventory; }
    @GetMapping public Map<String, Object> get(@PathVariable long characterId) throws Exception { return inventory.read(characterId); }
    @PostMapping public Map<String, String> change(@PathVariable long characterId, @RequestBody InventoryService.Change change) throws Exception {
        inventory.change(characterId, change); return Collections.singletonMap("message", "背包已更新");
    }
}
