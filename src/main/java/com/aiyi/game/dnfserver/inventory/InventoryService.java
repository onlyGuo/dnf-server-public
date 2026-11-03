package com.aiyi.game.dnfserver.inventory;

import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.entity.common.Item;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.aiyi.game.dnfserver.entity.stackable.StackableType;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.security.OwnershipService;
import com.xiaoyouma.dnf.parser.pvf.model.Pvf;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

@Service
public class InventoryService {
    private final DataSource source;
    private final OwnershipService ownership;
    private final PvfManager pvfManager;
    private Pvf loadedPvf;
    private final Map<Long, ItemAndType> items = new HashMap<>();
    private static final Set<String> EDIT_FIELDS = new HashSet<>(Arrays.asList("id", "seal", "enhancement", "sealCount", "amountOrGrade", "durability", "orb", "increaseType", "increaseValue", "forge"));

    public InventoryService(DataSource source, OwnershipService ownership, PvfManager pvfManager) {
        this.source = source; this.ownership = ownership; this.pvfManager = pvfManager;
    }

    public Map<String, Object> read(long characterId) throws Exception {
        CharacInfo character = ownership.requireCharacter(characterId);
        try (Connection c = source.getConnection()) {
            Boolean online = online(c, character.getMid(), false);
            try (PreparedStatement st = c.prepareStatement("SELECT inventory, money, coin, pay_coin, inventory_capacity FROM taiwan_cain_2nd.inventory WHERE charac_no=?")) {
                st.setLong(1, characterId);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("角色没有背包数据");
                    byte[] blob = rs.getBytes(1);
                    InventoryCodec inventory = InventoryCodec.read(blob);
                    List<Map<String, Object>> slots = new ArrayList<>();
                    for (int i = 0; i < inventory.size(); i++) {
                        Map<String, Object> slot = inventory.slot(i);
                        long id = ((Number) slot.get("id")).longValue();
                        if (id != 0) {
                            ItemAndType item = lookup(id);
                            if (item != null) slot.put("item", item.item);
                        }
                        slots.add(slot);
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("characterId", characterId); result.put("online", online);
                    result.put("revision", digest(blob)); result.put("slots", slots);
                    result.put("money", rs.getLong(2)); result.put("coin", rs.getLong(3));
                    result.put("payCoin", rs.getLong(4)); result.put("capacity", rs.getLong(5));
                    return result;
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void change(long characterId, Change change) throws Exception {
        CharacInfo character = ownership.requireCharacter(characterId);
        if (change == null || change.revision == null) throw new IllegalArgumentException("请刷新背包后重试");
        Connection c = DataSourceUtils.getConnection(source);
        try {
            Boolean online = online(c, character.getMid(), true);
            if (online == null || online) throw new IllegalArgumentException("角色账号在线或状态未知，请先退出游戏后编辑背包");
            byte[] blob;
            long capacity;
            try (PreparedStatement st = c.prepareStatement("SELECT inventory, inventory_capacity FROM taiwan_cain_2nd.inventory WHERE charac_no=? FOR UPDATE")) {
                st.setLong(1, characterId);
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("角色没有背包数据");
                    blob = rs.getBytes(1);
                    capacity = rs.getLong(2);
                }
            }
            if (!digest(blob).equals(change.revision)) throw new IllegalArgumentException("背包已被其他操作修改，请刷新后重试");
            InventoryCodec inventory = InventoryCodec.read(blob);
            if ("move".equals(change.action)) {
                if (change.to == null) throw new IllegalArgumentException("请选择目标位置");
                int from = change.index, to = change.to;
                requireEditablePosition(from);
                if (from == to) throw new IllegalArgumentException("源位置和目标位置相同");
                Map<String, Object> sourceSlot = inventory.slot(from), destSlot = inventory.slot(to);
                if (((Number) sourceSlot.get("id")).longValue() == 0) throw new IllegalArgumentException("原位置没有物品");
                requirePosition(to, ((Number) sourceSlot.get("type")).intValue());
                requireAvailablePosition(to, capacity);
                if (((Number) destSlot.get("id")).longValue() != 0) {
                    int displacedType = ((Number) destSlot.get("type")).intValue();
                    if (!isQuickSlot(from) && isQuickSlot(to)) {
                        int empty = findEmptyBagSlot(inventory, displacedType, capacity);
                        if (empty < 0) throw new IllegalArgumentException("背包栏位不足，无法将快捷栏原有物品移回背包");
                        inventory.swap(to, empty);
                    } else {
                        requirePosition(from, displacedType);
                    }
                }
                inventory.swap(from, to);
            } else if ("edit".equals(change.action)) {
                Map<String, Object> old = inventory.slot(change.index);
                Map<String, Object> values = change.changes == null ? new HashMap<>() : new HashMap<>(change.changes);
                if (!EDIT_FIELDS.containsAll(values.keySet())) throw new IllegalArgumentException("不支持的物品字段");
                long oldId = ((Number) old.get("id")).longValue();
                long itemId = parseId(values.containsKey("id") ? values.get("id") : oldId);
                int type = ((Number) old.get("type")).intValue();
                if (itemId == 0) throw new IllegalArgumentException("物品 ID 不能为空");
                if (oldId == 0) requireAvailablePosition(change.index, capacity);
                if (itemId != oldId) {
                    ItemAndType item = lookup(itemId);
                    if (item == null || item.type == 0) throw new IllegalArgumentException("无法从 PVF 确定该物品分类");
                    if (oldId != 0 && item.type != type) throw new IllegalArgumentException("只能替换为相同分类的物品");
                    if (oldId == 0) { inventory.clear(change.index); type = item.type; values.put("type", type); values.putIfAbsent("amountOrGrade", 1); }
                }
                requirePosition(change.index, type);
                inventory.edit(change.index, values);
            } else if ("delete".equals(change.action)) {
                requireEditablePosition(change.index);
                inventory.clear(change.index);
            } else throw new IllegalArgumentException("不支持的背包操作");
            try (PreparedStatement st = c.prepareStatement("UPDATE taiwan_cain_2nd.inventory SET inventory=? WHERE charac_no=? AND inventory=?")) {
                st.setBytes(1, inventory.write()); st.setLong(2, characterId); st.setBytes(3, blob);
                if (st.executeUpdate() != 1) throw new IllegalArgumentException("背包已被其他操作修改，请刷新后重试");
            }
        } finally { DataSourceUtils.releaseConnection(c, source); }
    }

    private static Boolean online(Connection c, long accountId, boolean lock) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = c.getMetaData().getTables("taiwan_login", null, "login_account_%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && name.matches("login_account_[0-9]+")) tables.add(name);
            }
        }
        Collections.sort(tables);
        boolean seen = false;
        // An account can be active in another group. Check and lock every
        // login table rather than assuming group 3 is the only one in use.
        for (String table : tables) try (PreparedStatement st = c.prepareStatement("SELECT login_status FROM taiwan_login.`" + table + "` WHERE m_id=?" + (lock ? " FOR UPDATE" : ""))) {
            st.setLong(1, accountId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) { seen = true; if (rs.getInt(1) != 0) return true; }
            }
        }
        return seen ? Boolean.FALSE : null;
    }

    private synchronized ItemAndType lookup(long id) {
        Pvf pvf = pvfManager.getPvf();
        if (pvf == null) return null;
        if (pvf != loadedPvf) { loadedPvf = pvf; items.clear(); }
        if (items.containsKey(id)) return items.get(id);
        ItemAndType result = null;
        try {
            String key = Long.toString(id);
            JSONObject list = pvf.getScript("equipment/equipment.lst");
            if (list != null && list.containsKey(key)) {
                String path = list.getStr(key); if (path.startsWith("/")) path = path.substring(1);
                JSONObject script = pvf.getScript("equipment/" + path);
                Equipment item = Equipment.forScript(script); item.setId((int) id);
                String equipmentType = script.getStr("[equipment type]", "").toLowerCase(Locale.ROOT);
                int type = equipmentType.contains("avatar") || equipmentType.contains("creature") || equipmentType.contains("artifact") ? 0 : 1;
                result = new ItemAndType(item, type);
            } else {
                list = pvf.getScript("stackable/stackable.lst");
                if (list != null && list.containsKey(key)) {
                    String path = list.getStr(key); if (path.startsWith("/")) path = path.substring(1);
                    Stackable item = Stackable.forScript(pvf.getScript("stackable/" + path)); item.setId((int) id);
                    StackableType type = item.getStackableType();
                    int code = type == StackableType.material || type == StackableType.recipe ? 3 :
                            type == StackableType.quest ? 4 : type == StackableType.material_expert_job ? 10 :
                            type == StackableType.feed || type == StackableType.creature || type == null ? 0 : 2;
                    result = new ItemAndType(item, code);
                }
            }
        } catch (RuntimeException ignored) { /* Unknown PVF items remain visible by numeric ID. */ }
        items.put(id, result);
        return result;
    }

    private static void requireEditablePosition(int index) {
        if (index < 3 || index >= 249) throw new IllegalArgumentException("此背包位置不支持编辑");
    }
    private static boolean isQuickSlot(int index) { return index >= 3 && index < 9; }

    private static int bagStart(int type) {
        switch (type) {
            case 1: return 9;
            case 2: return 57;
            case 3: return 105;
            case 4: return 153;
            case 10: return 201;
            default: throw new IllegalArgumentException("快捷栏物品没有对应的背包分类，无法移动");
        }
    }

    private static int availableBagSlots(long capacity) {
        // The game uses a 32-slot base and an expansion of 0, 8 or 16 slots.
        int expansion = (int) (capacity & 0xffffL);
        if (expansion != 0 && expansion != 8 && expansion != 16)
            throw new IllegalArgumentException("无法识别背包扩容状态，无法移动物品");
        return 32 + expansion;
    }

    private static void requireAvailablePosition(int index, long capacity) {
        requireEditablePosition(index);
        if (isQuickSlot(index)) return;
        int start = index < 57 ? 9 : index < 105 ? 57 : index < 153 ? 105 : index < 201 ? 153 : 201;
        if (index >= start + availableBagSlots(capacity))
            throw new IllegalArgumentException("背包栏位不足，目标位置尚未解锁");
    }

    private static int findEmptyBagSlot(InventoryCodec inventory, int type, long capacity) {
        int start = bagStart(type);
        int end = Math.min(inventory.size(), start + availableBagSlots(capacity));
        for (int index = start; index < end; index++) {
            if (((Number) inventory.slot(index).get("id")).longValue() == 0) return index;
        }
        return -1;
    }

    private static void requirePosition(int index, int type) {
        requireEditablePosition(index);
        int expected = index < 9 ? 0 : index < 57 ? 1 : index < 105 ? 2 : index < 153 ? 3 : index < 201 ? 4 : 10;
        if (type != 1 && type != 2 && type != 3 && type != 4 && type != 10) throw new IllegalArgumentException("该物品类型不能放在物品栏");
        if (expected != 0 && expected != type) throw new IllegalArgumentException("物品类型与目标背包分区不匹配");
    }
    private static long parseId(Object value) {
        try { long id = Long.parseLong(String.valueOf(value)); if (id < 0 || id > Integer.MAX_VALUE) throw new NumberFormatException(); return id; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("物品 ID 无效"); }
    }
    private static String digest(byte[] blob) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(blob);
        StringBuilder value = new StringBuilder(); for (byte b : hash) value.append(String.format("%02x", b & 255)); return value.toString();
    }
    private static class ItemAndType { final Item item; final int type; ItemAndType(Item item, int type) { this.item = item; this.type = type; } }
    public static class Change { public String revision; public String action; public int index; public Integer to; public Map<String, Object> changes; }
}
