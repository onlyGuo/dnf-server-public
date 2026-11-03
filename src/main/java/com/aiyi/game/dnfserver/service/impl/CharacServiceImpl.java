package com.aiyi.game.dnfserver.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.aiyi.core.beans.Method;
import com.aiyi.core.beans.ResultPage;
import com.aiyi.core.beans.WherePrams;
import com.aiyi.core.sql.where.C;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.entity.*;
import com.aiyi.game.dnfserver.entity.task.TaskType;
import com.aiyi.game.dnfserver.pvf.PvfManager;
import com.aiyi.game.dnfserver.service.CharacService;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.security.OwnershipService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import com.xiaoyouma.dnf.parser.pvf.model.Pvf;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 * 角色相关业务
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 14:25
 */
@Service
public class CharacServiceImpl implements CharacService {

    @Resource
    private CharacInfoDao characInfoDao;
    @Resource
    private AccountDao accountDao;
    @Resource
    private AccountVODao accountVODao;

    @Resource
    private PvfManager pvfManager;

    @Resource
    private PostalService postalService;

    @Resource
    private OwnershipService ownership;

    @Override
    public ResultPage<CharacInfo> list(Integer levMin, Integer levMax, Integer job,
                                       String name, String account, Integer page, Integer pageSize) {
        if (null == levMin) {
            levMin = 1;
        }
        if (null == levMax) {
            levMax = 999;
        }
        WherePrams where = Method.where(CharacInfo::getLev, C.DE, levMin).and(CharacInfo::getLev, C.XE, levMax);
        if (null != job){
            where.and(CharacInfo::getJob, C.EQ, job);
        }

        if (!StringUtils.isEmpty(name)){
            where.and(CharacInfo::getCharacName, C.LIKE, ChinaseUtil.toTraditional(name));
        }

        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!StringUtils.isEmpty(account)){
            WherePrams where1 = Method.where(AccountVO::getAccountname, C.LIKE, account);
            if (!accountVO.isSuperAdmin()){
                where1.and(AccountVO::getParentUid, C.EQ, ThreadUtil.getUserId());
            }
            List<Long> ids = accountVODao.list(where1)
                    .stream().map(AccountVO::getUid).collect(Collectors.toList());
            if (ids.isEmpty()){
                return new ResultPage<>(0, 0, 10, new ArrayList<>());
            }
            where.and(CharacInfo::getMid, C.IN, ids);
        }else{
            if (!accountVO.isSuperAdmin()){
                // 非超管只能查看下级账号的角色
                List<Long> collect = accountVODao.list(Method
                                .where(AccountVO::getParentUid, C.EQ, ThreadUtil.getUserId()))
                        .stream().map(AccountVO::getUid).collect(Collectors.toList());
                if (collect.isEmpty()) return new ResultPage<>(0, page, pageSize, new ArrayList<>());
                where.and(CharacInfo::getMid, C.IN, collect);
            }
        }
        ResultPage<CharacInfo> list = characInfoDao.list(where, page, pageSize);
        if (!list.getList().isEmpty()){
            Set<Long> collect = list.getList().stream().map(CharacInfo::getMid).collect(Collectors.toSet());
            Map<Long, AccountVO> mapper = accountVODao.list(Method.where(AccountVO::getUid, C.IN, collect.toArray()))
                    .stream().collect(Collectors.toMap(AccountVO::getUid, a -> a));
            list.getList().forEach(characInfo -> {
                if (mapper.containsKey(characInfo.getMid())){
                    characInfo.setAccountname(mapper.get(characInfo.getMid()).getAccountname());
                }
            });
        }
        return list;
    }

    @Override
    public List<CharacInfo> list(String account) {
        AccountVO accountVO = accountVODao.get(Method
                .where(AccountVO::getAccountname, C.EQ, account));
        if (null == accountVO){
            return new LinkedList<>();
        }
        ownership.requireAccount(accountVO.getUid());

        List<CharacInfo> list = characInfoDao.list(Method
                .where(CharacInfo::getMid, C.EQ, accountVO.getUid()));

        for (CharacInfo characInfo: list){
            ChinaseUtil.toSimple(characInfo);
        }
        return list;
    }

    @Override
    public void update(CharacInfo info) {
        CharacInfo characInfo = ownership.requireCharacter(info.getCharacNo());
        // 三速
        characInfo.setAttackSpeed(info.getAttackSpeed());
        characInfo.setCastSpeed(info.getCastSpeed());
        characInfo.setMoveSpeed(info.getMoveSpeed());

        // 疲劳
        characInfo.setFatigue(info.getFatigue());

        // 红蓝
        characInfo.setMaxHp(info.getMaxHp());
        characInfo.setMaxMp(info.getMaxMp());

        // 伤害
        characInfo.setMagAttack(info.getMagAttack());
        characInfo.setMagDefense(info.getMagDefense());
        characInfo.setPhyAttack(info.getPhyAttack());
        characInfo.setPhyDefense(info.getPhyDefense());

        // 硬直、跳跃
        characInfo.setHitRecovery(info.getHitRecovery());
        characInfo.setJump(info.getJump());

        characInfoDao.update(characInfo);
    }

    @Override
    public void overTasks(Long characId) {
        ownership.requireCharacter(characId);
        List<TaskVO> tasks = listNoOverTasks(characId);
        tasks.forEach(task -> {
            if (task.getType().isCatFinish()){
                // 直接完成
                characInfoDao.execute("UPDATE `taiwan_cain`.`new_charac_quest` SET play_" + task.getNo() +
                        "_trigger = 0 WHERE charac_no = ?", characId);
            }else{
                // 物品收集类任务，发邮件任务材料
                for (TaskItemVO item: task.getItems()){
                    Postal postal = new Postal();
                    postal.setItemId(item.getId());
                    postal.setAddInfo(item.getCount());
                    postal.setReceiveCharacNo(characId + "");
                    postal.setSendCharacName("Task materials");
                    postalService.sendMail(postal);
                }
            }
        });
    }

    @Override
    public List<TaskVO> listNoOverTasks(Long characId) {
        List<Map<String, Object>> maps = characInfoDao
                .listBySql("SELECT * FROM `taiwan_cain`.`new_charac_quest` WHERE charac_no = ?", characId);
        if (maps.isEmpty()){
            return new ArrayList<>();
        }
        Pvf pvf = pvfManager.getPvf();
        if (pvf == null) throw new IllegalArgumentException("游戏服务中未配置可解析的 Script.pvf，无法读取任务资料");
        JSONObject questList = pvf.getScript("n_quest/quest.lst");
        List<TaskVO> tasks = new ArrayList<>();
        maps.forEach(map -> {
            for (int i = 1; i <= 20; i++){
                int taskId = Integer.parseInt(map.get("play_" + i).toString());
                int trigger = Integer.parseInt(map.get("play_" + i + "_trigger").toString());
                if (taskId > 0 && trigger > 0){
                    TaskVO taskVO = new TaskVO();
                    taskVO.setId(taskId);
                    taskVO.setNo(i);
                    tasks.add(taskVO);
                    if (!questList.containsKey(taskId + "")){
                        continue;
                    }
                    JSONObject script = pvf.getScript("n_quest/" + questList.getStr(taskId + ""));
                    String typeStr = script.getJSONArray("[type]").getStr(0);
                    taskVO.setType(TaskType.forType(typeStr));
                    if (!taskVO.getType().isCatFinish()){
                        List<TaskItemVO> taskItemS = new ArrayList<>();
                        JSONArray jsonArray = script.getJSONArray("[int data]");
                        for (int j = 0; j < jsonArray.size(); j++) {
                            TaskItemVO taskItemVO = new TaskItemVO();
                            taskItemVO.setId(jsonArray.getInt(j));
                            taskItemVO.setCount(jsonArray.getInt(++j));
                            taskItemS.add(taskItemVO);
                        }
                        taskVO.setItems(taskItemS);
                    }
                }
            }
        });
        return tasks;
    }
}
