package com.aiyi.game.dnfserver.entity;

import java.util.List;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * 数据库快捷操作实体
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/29 14:38
 */
public class DataBaseExcute {

    private int type;

    private String script;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }


}
