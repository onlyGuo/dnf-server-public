package com.aiyi.game.dnfserver.controller;

import java.util.function.Function;

import com.aiyi.game.dnfserver.entity.common.AttachType;
import com.aiyi.game.dnfserver.entity.common.UsableJob;
import com.aiyi.game.dnfserver.entity.equipment.EquipmentType;
import com.aiyi.game.dnfserver.entity.stackable.StackableType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 枚举相关接口
 * @author xiatian
 */
@RestController
@com.aiyi.game.dnfserver.conf.GmAllowed
@RequestMapping("api/v1/enums")
public class EnumsController {

    /**
     * 获取物品绑定/交易类型枚举列表
     * @return 枚举列表
     */
    @GetMapping("attach-types")
    public List<Map<String, String>> getAttachTypes() {
        return enumToList(AttachType.class, AttachType::getDesc);
    }

    /**
     * 物品类型枚举列表
     * @return 枚举列表
     */
    @GetMapping("stackable-types")
    public List<Map<String, String>> getStackableTypes() {
        return enumToList(StackableType.class, StackableType::getDesc);
    }

    /**
     * 可使用职业枚举列表
     * @return 枚举列表
     */
    @GetMapping("usable-jobs")
    public List<Map<String, String>> getUsableJobs() {
        return enumToList(UsableJob.class, UsableJob::getJobDesc);
    }

    /**
     * 装备类型列表
     * @return 枚举列表
     */
    @GetMapping("equipment-types")
    public List<Map<String, String>> getEquipmentTypes() {
        return enumToList(EquipmentType.class, EquipmentType::getDesc);
    }


    /**
     * 将枚举转换为列表形式
     * @param enumClass 枚举类
     * @param descExtractor 描述提取器
     * @param <E> 枚举类型
     * @return 列表形式的枚举
     */
    public static <E extends Enum<E>> List<Map<String, String>> enumToList(
            Class<E> enumClass,
            Function<E, String> descExtractor) {
        Objects.requireNonNull(enumClass, "enumClass must not be null");
        Objects.requireNonNull(descExtractor, "descExtractor must not be null");

        E[] constants = enumClass.getEnumConstants();
        List<Map<String, String>> list = new ArrayList<>(constants.length);
        for (E e : constants) {
            Map<String, String> m = new LinkedHashMap<>(2);
            m.put("key", e.name());
            m.put("desc", descExtractor.apply(e));
            list.add(m);
        }
        return list;
    }

}
