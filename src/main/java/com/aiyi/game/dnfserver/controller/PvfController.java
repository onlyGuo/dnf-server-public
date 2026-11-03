package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.channel.ChannelService;
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
import com.aiyi.game.dnfserver.pvf.ImagePackStorage;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.pvf.PvfUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
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

    @Resource
    private ImagePackStorage imagePackStorage;

    @Resource
    private PvfUploadService pvfUploads;

    @Resource
    private ChannelService channels;

    private static final Logger LOGGER = LoggerFactory.getLogger(PvfController.class);

    @GetMapping("imagepacks/status")
    @com.aiyi.game.dnfserver.conf.GmAllowed
    public Map<String, Object> imagePackStatus() {
        Map<String, Object> status = imagePackStorage.status();
        AccountVO account = accountVODao.get(ThreadUtil.getUserId());
        status.put("canUpload", account != null && account.isSuperAdmin());
        return status;
    }

    @PostMapping("imagepacks/upload")
    public Map<String, Object> uploadImagePacks(@RequestParam("file") MultipartFile file) {
        AccountVO account = accountVODao.get(ThreadUtil.getUserId());
        if (account == null || !account.isSuperAdmin()) {
            throw new com.aiyi.game.dnfserver.conf.ForbiddenException("只有超管可以上传 ImagePacks2");
        }
        return imagePackStorage.upload(file);
    }

    @GetMapping("info")
    @com.aiyi.game.dnfserver.conf.GmAllowed
    private Map<String, Object> getPvfInfo() {
        HashMap<String, Object> result = new HashMap<>();
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        boolean gameAvailable = channels.canReloadPvf();
        result.put("canUpload", accountVO != null && accountVO.isSuperAdmin() && gameAvailable);
        result.put("gameAvailable", gameAvailable);
        result.put("available", false);
        result.put("size", "未配置");
        result.put("equipmentCount", 0);
        result.put("itemCount", 0);
        if (Files.isRegularFile(pvfManager.getPvfPath())) {
            try {
                PvfManager.Catalog catalog = pvfManager.getCatalog();
                int size = catalog.getSizeKb();
                result.put("size", size > 1024 ? String.format("%.2f MB", size / 1024.0) : size + " KB");
                result.put("equipmentCount", catalog.getEquipment().size());
                result.put("itemCount", catalog.getStackable().size());
                result.put("available", true);
            } catch (RuntimeException e) {
                LOGGER.warn("无法读取游戏 PVF 物品列表", e);
                result.put("error", "游戏 PVF 解析失败，请检查或重新上传");
            }
        }
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
    @com.aiyi.game.dnfserver.conf.GmAllowed
    public ResultPage<Item> search(String keyword, String type, String subType, Integer page, Integer pageSize) {
        PvfManager.Catalog catalog = ensurePvfCache();
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
            for (Equipment equipment : catalog.getEquipment()) {
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
            for (Stackable stackable : catalog.getStackable()) {
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
    public void img(String path, int index, @RequestParam(defaultValue = "false") boolean frame, HttpServletResponse response) {
        byte[] imageBytes = frame ? npkManager.getImageFrameBytes(path, index) : npkManager.getImageBytes(path, index);
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-store");
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
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws Exception {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (accountVO == null || !accountVO.isSuperAdmin()) {
            throw new ValidationException("您不能上传PVF");
        }
        return pvfUploads.upload(file);
    }

    private PvfManager.Catalog ensurePvfCache() {
        if (!Files.isRegularFile(pvfManager.getPvfPath())) {
            throw new ValidationException("游戏服务中未配置 Script.pvf，物品列表不可用；可直接输入物品 ID");
        }
        return pvfManager.getCatalog();
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

}
