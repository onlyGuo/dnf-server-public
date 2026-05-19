package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.common.Item;
import com.aiyi.game.dnfserver.entity.common.ItemType;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.equipment.EquipmentType;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.aiyi.game.dnfserver.entity.stackable.StackableType;
import com.aiyi.game.dnfserver.pvf.NpkManager;
import com.aiyi.game.dnfserver.pvf.PvfCache;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.utils.MinFieldUtil;
import com.aiyi.game.dnfserver.utils.cache.CacheUtil;
import com.aiyi.game.dnfserver.utils.cache.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pvf相关接口
 *
 * @author gsk
 */
@RestController
@RequestMapping("api/v1/pvf")
public class PvfController {

    @Resource
    private AccountVODao accountVODao;

    @Resource
    private PvfManager pvfManager;

    @Resource
    private NpkManager npkManager;

    private static final Logger LOGGER = LoggerFactory.getLogger(PvfController.class);

    @GetMapping("info")
    private Map<String, Object> getPvfInfo() {
        ensurePvfCache();
        HashMap<String, Object> result = new HashMap<>();
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        result.put("canUpload", accountVO.isAdmin());
        int size = PvfCache.getSize();
        String fileSizeStr = size + " KB";
        if (size > 1024) {
            fileSizeStr = String.format("%.2f MB", size / 1024.0);
        }
        result.put("size", fileSizeStr);
        result.put("equipmentCount", PvfCache.getEquipmentList().size());
//        result.put("equipmentList", PvfCache.getEquipmentList());
        result.put("itemCount", PvfCache.getStackableList().size());
//        result.put("itemList", PvfCache.getStackableList());
        return result;
    }

    /**
     * 分页查询物品
     *
     * @param keyword  关键字(名称或ID)
     * @param type     物品类型: 装备/道具 (equipment/stackable)
     * @param subType  细分类型: EquipmentType 或 StackableType
     * @param page     页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping("search")
    public ResultPage<Item> search(String keyword, String type, String subType, Integer page, Integer pageSize) {
        ensurePvfCache();
        if (null == page) {
            page = 1;
        }
        if (null == pageSize) {
            pageSize = 10;
        }

        boolean wantEquipment = isEquipmentType(type);
        boolean wantStackable = isStackableType(type);
        if (!wantEquipment && !wantStackable) {
            wantEquipment = true;
            wantStackable = true;
        }

        String keywordTrim = keyword == null ? "" : keyword.trim();
        Integer keywordId = tryParseInt(keywordTrim);

        EquipmentType equipmentType = wantEquipment ? parseEquipmentType(subType) : null;
        StackableType stackableType = wantStackable ? parseStackableType(subType) : null;

        List<Item> filtered = new ArrayList<>();
        if (wantEquipment) {
            for (Equipment equipment : safeEquipmentList()) {
                if (equipmentType != null && equipment.getEquipmentType() != equipmentType) {
                    continue;
                }
                if (!matchKeyword(equipment, keywordTrim, keywordId)) {
                    continue;
                }
                filtered.add(equipment);
            }
        }
        if (wantStackable) {
            for (Stackable stackable : safeStackableList()) {
                if (stackableType != null && stackable.getStackableType() != stackableType) {
                    continue;
                }
                if (!matchKeyword(stackable, keywordTrim, keywordId)) {
                    continue;
                }
                filtered.add(stackable);
            }
        }

        int total = filtered.size();
        int fromIndex = Math.max(0, (page - 1) * pageSize);
        if (fromIndex >= total) {
            return new ResultPage<>(total, page, pageSize, new ArrayList<>());
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<Item> list = filtered.subList(fromIndex, toIndex);
        return new ResultPage<>(total, page, pageSize, list);
    }

    @GetMapping("img")
    @NoLogin
    public void img(String path, int index, HttpServletResponse response) {
        byte[] imageBytes = npkManager.getImageBytes(path, index);
        response.setContentType("image/png");
        response.setHeader("Content-Disposition", "inline;filename=" + path + "@" + index + ".png");
        OutputStream out = null;
        try {
            out = response.getOutputStream();
            out.write(imageBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (null != out) {
                try {
                    out.close();
                } catch (IOException e) {
                    LOGGER.error("关闭输出流失败", e);
                }
            }
        }
    }

    @PostMapping("upload")
    public void upload(MultipartFile file) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isAdmin()) {
            throw new ValidationException("您不能上传PVF");
        }
        // 临时保存
        CacheUtil.lock(20000, Key.as("pvf_upload_lock"), () -> {
            MinFieldUtil.cpyFile("Script.pvf", "Script_backup.pvf");
            try {
                MinFieldUtil.writeFile("Script.pvf", file.getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                pvfManager.init();
                int size = MinFieldUtil.getFileSizeKB(new File("Script.pvf").getAbsolutePath());
                List<Equipment> equipmentList = pvfManager.getEquipmentList();
                List<Stackable> stackableList = pvfManager.getStackableList();
                PvfCache.setPvfSize(size);
                PvfCache.setEquipmentList(equipmentList);
                PvfCache.setStackableList(stackableList);
            } catch (Exception e) {
                // 恢复备份文件
                MinFieldUtil.cpyFile("Script_backup.pvf", "Script.pvf");
                pvfManager.init();
                throw new ValidationException("PVF文件解析失败，已恢复为原文件，请检查后重新上传！错误信息：" + e.getMessage());
            }
            return null;
        });
    }

    private void ensurePvfCache() {
        int size = PvfCache.getSize();
        if (0 == size) {
            size = MinFieldUtil.getFileSizeKB(new File("data/Script.pvf").getAbsolutePath());
            PvfCache.setPvfSize(size);
            System.out.println(PvfCache.getSize());
            List<Equipment> equipmentList = pvfManager.getEquipmentList();
            PvfCache.setEquipmentList(equipmentList);
            List<Stackable> stackableList = pvfManager.getStackableList();
            PvfCache.setStackableList(stackableList);
        }
    }

    private boolean matchKeyword(Item item, String keyword, Integer keywordId) {
        if (keyword == null || keyword.isEmpty()) {
            return true;
        }
        if (keywordId != null && item.getId() == keywordId) {
            return true;
        }
        String name = item.getName();
        return name != null && name.contains(keyword);
    }

    private boolean isEquipmentType(String type) {
        return ItemType.equipment.getType().equalsIgnoreCase(type) ||
                ItemType.equipment.getDesc().equals(type);
    }

    private boolean isStackableType(String type) {
        return ItemType.stackable.getType().equalsIgnoreCase(type) ||
                ItemType.stackable.getDesc().equals(type);
    }

    private EquipmentType parseEquipmentType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        for (EquipmentType t : EquipmentType.values()) {
            if (t.name().equalsIgnoreCase(type) || t.getDesc().equals(type) || t.getType().equalsIgnoreCase(type)) {
                return t;
            }
        }
        return null;
    }

    private StackableType parseStackableType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        for (StackableType t : StackableType.values()) {
            if (t.name().equalsIgnoreCase(type) || t.getDesc().equals(type) || t.getType().equalsIgnoreCase(type)) {
                return t;
            }
        }
        return null;
    }

    private Integer tryParseInt(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<Equipment> safeEquipmentList() {
        List<Equipment> list = PvfCache.getEquipmentList();
        return list == null ? new ArrayList<>() : list;
    }

    private List<Stackable> safeStackableList() {
        List<Stackable> list = PvfCache.getStackableList();
        return list == null ? new ArrayList<>() : list;
    }
}
