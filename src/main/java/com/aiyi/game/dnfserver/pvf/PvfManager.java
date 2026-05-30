package com.aiyi.game.dnfserver.pvf;

import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.entity.equipment.Equipment;
import com.aiyi.game.dnfserver.entity.equipment.EquipmentType;
import com.aiyi.game.dnfserver.entity.stackable.Stackable;
import com.aiyi.game.dnfserver.entity.stackable.StackableType;
import com.xiaoyouma.dnf.parser.pvf.coder.PvfCoder;
import com.xiaoyouma.dnf.parser.pvf.model.Pvf;
import com.xiaoyouma.dnf.parser.pvf.model.PvfFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author xiatian
 */
@Component
public class PvfManager {

    Logger logger = LoggerFactory.getLogger(PvfManager.class);

    @PostConstruct
    public void init(){
        File file = new File("data/Script.pvf");
        if (!file.exists()){
            logger.warn("Script.pvf not found!");
        }else{
            PvfCoder.initialize(file.getAbsolutePath(), Charset.forName("Big5"));
        }
    }

    public Pvf getPvf(){
        return PvfCoder.getPvf();
    }

    /**
     * 获取装备列表
     */
    public List<Equipment> getEquipmentList(){
        Pvf pvf = getPvf();
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
        Pvf pvf = getPvf();
        List<Stackable> stackableList = new ArrayList<>();
        JSONObject script = pvf.getScript("stackable/stackable.lst");
        for (String key : script.keySet()) {
            if (key.equalsIgnoreCase("1038")){
                System.out.println(script.getStr(key));
            }
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
