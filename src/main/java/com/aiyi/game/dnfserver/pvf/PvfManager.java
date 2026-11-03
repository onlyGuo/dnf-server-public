package com.aiyi.game.dnfserver.pvf;

import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.xiaoyouma.dnf.parser.pvf.model.Pvf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author xiatian
 */
@Component
public class PvfManager {

    private static final Logger logger = LoggerFactory.getLogger(PvfManager.class);
    private static final Charset PVF_CHARSET = Charset.forName("Big5");
    private final Path pvfPath;
    private volatile Catalog current;

    public PvfManager(@Value("${dnf.channels.game-dir:/home/neople/game}") String gameDir) {
        this.pvfPath = Paths.get(gameDir).toAbsolutePath().normalize().resolve("Script.pvf");
    }

    @PostConstruct
    public void init() {
        loadWhenAvailable();
    }

    public Path getPvfPath() { return pvfPath; }

    private synchronized void loadWhenAvailable() {
        if (current != null || !Files.isRegularFile(pvfPath)) return;
        try {
            Pvf pvf = new Pvf(pvfPath.toString(), PVF_CHARSET);
            current = new Catalog(pvf, sizeKb(pvfPath), null, null);
            logger.info("已从游戏目录加载 PVF：{}", pvfPath);
        } catch (IOException | RuntimeException e) {
            logger.warn("无法解析游戏目录中的 PVF {}：{}", pvfPath, e.getMessage());
        }
    }

    public Pvf getPvf() {
        if (current == null) loadWhenAvailable();
        Catalog catalog = current;
        return catalog == null ? null : catalog.pvf;
    }

    public synchronized Catalog getCatalog() {
        if (current == null) loadWhenAvailable();
        Catalog catalog = current;
        if (catalog == null) throw new IllegalStateException("未找到或无法解析游戏服务目录中的 Script.pvf");
        if (catalog.equipment == null || catalog.stackable == null) {
            catalog = new Catalog(catalog.pvf, catalog.sizeKb,
                    Collections.unmodifiableList(readEquipment(catalog.pvf)),
                    Collections.unmodifiableList(readStackable(catalog.pvf)));
            current = catalog;
        }
        return catalog;
    }

    /** Parse the uploaded temporary file before changing the live game files. */
    public Catalog parseCandidate(Path file) throws IOException {
        Pvf candidate = new Pvf(file.toString(), PVF_CHARSET);
        return new Catalog(candidate, sizeKb(file),
                Collections.unmodifiableList(readEquipment(candidate)),
                Collections.unmodifiableList(readStackable(candidate)));
    }

    public synchronized void activate(Catalog catalog) {
        current = catalog;
    }

    private static int sizeKb(Path path) throws IOException {
        return (int) Math.max(1, Files.size(path) / 1024);
    }

    public static final class Catalog {
        private final Pvf pvf;
        private final int sizeKb;
        private final List<Equipment> equipment;
        private final List<Stackable> stackable;

        private Catalog(Pvf pvf, int sizeKb, List<Equipment> equipment, List<Stackable> stackable) {
            this.pvf = pvf; this.sizeKb = sizeKb; this.equipment = equipment; this.stackable = stackable;
        }
        public int getSizeKb() { return sizeKb; }
        public List<Equipment> getEquipment() { return equipment; }
        public List<Stackable> getStackable() { return stackable; }
    }

    /**
     * 获取装备列表
     */
    public List<Equipment> getEquipmentList(){
        return getCatalog().getEquipment();
    }

    private List<Equipment> readEquipment(Pvf pvf) {
        List<Equipment> equipmentList = new ArrayList<>();
        JSONObject script = pvf.getScript("equipment/equipment.lst");
        for (String key : script.keySet()) {
            String str = script.getStr(key);
            if (str.startsWith("/")){
                str = str.substring(1);
            }
            JSONObject equipmentScript = pvf.getScript("equipment/" + str);
            Equipment equipment = Equipment.forScript(equipmentScript);
            equipment.setId(Integer.parseInt(key));
            if ("未命名".equals(equipment.getName()) || equipment.getName().contains("找不到代码") || equipment.getName().trim().isEmpty()){
                continue;
            }
            equipmentList.add(equipment);
        }
        return equipmentList;
    }

    /**
     * 获取道具列表
     */
    public List<Stackable> getStackableList(){
        return getCatalog().getStackable();
    }

    private List<Stackable> readStackable(Pvf pvf) {
        List<Stackable> stackableList = new ArrayList<>();
        JSONObject script = pvf.getScript("stackable/stackable.lst");
        for (String key : script.keySet()) {
            String str = script.getStr(key);
            String path = "stackable/" + str;
            if (str.startsWith("/")){
                path = "stackable" + str;
            }
            JSONObject stackableScript = pvf.getScript(path);
            Stackable stackable = Stackable.forScript(stackableScript);
            stackable.setId(Integer.parseInt(key));
            if ("未命名".equals(stackable.getName()) || stackable.getName().contains("找不到代码") || stackable.getName().trim().isEmpty()){
                continue;
            }
            stackableList.add(stackable);
        }
        return stackableList;
    }
}
