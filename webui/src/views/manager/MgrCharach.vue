<template>
    <div>
        <el-breadcrumb separator="/" style="margin-bottom: 30px">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>玩家管理</el-breadcrumb-item>
            <el-breadcrumb-item>角色列表</el-breadcrumb-item>
        </el-breadcrumb>

        <div>
            <!-- 搜索条件 -->
            <div style="margin: 20px auto;">
                <el-form :inline="true" :model="from" class="demo-form-inline">
                    <el-form-item label="账号">
                        <el-input v-model="from.account" placeholder="所属账号"></el-input>
                    </el-form-item>
                    <el-form-item label="昵称">
                        <el-input v-model="from.name" placeholder="角色名称"></el-input>
                    </el-form-item>
                    <el-form-item label="等级" style="margin-right: 10px">
                        <el-input v-model="from.minLev" placeholder="最低等级" type="number" style="width: 100px" min="1" max="86"></el-input>
                    </el-form-item>
                    <el-form-item label="~">
                        <el-input v-model="from.maxLev" placeholder="最高等级" type="number" style="width: 100px" min="1" max="86"></el-input>
                    </el-form-item>

                    <el-form-item label="职业">
                        <el-select v-model="from.job" placeholder="选择职业" style="width: 120px">
                            <el-option label="全部职业" value=""></el-option>
                            <el-option v-for="(job, index) in jobs" :label="job.name" :value="index" v-bind:key="index"></el-option>
                        </el-select>
                    </el-form-item>
<!--                    <el-form-item label="是否转职">-->
<!--                        <el-switch v-model="from.superJob" />-->
<!--                    </el-form-item>-->
                    <el-form-item>
                        <el-button type="primary" @click="load">查询</el-button>
                    </el-form-item>
                </el-form>
            </div>

            <el-table
                :data="tableData.list"
                border
                v-loading="loading"
                style="width: 100%">
                <el-table-column fixed prop="characNo" label="ID" min-width="50" />
                <el-table-column prop="characName" label="昵称" min-width="120"/>
              <el-table-column prop="accountname" label="所属账号" min-width="120"/>
                <el-table-column prop="job" label="职业" min-width="100">
                    <template #default="scope">
                        <span v-text="jobs[scope.row.job].name"></span>
                    </template>
                </el-table-column>
                <el-table-column prop="lev" label="等级" width="60" />
                <el-table-column prop="growType" label="转职类型" width="100">
                    <template #default="scope">
                        <span v-text="jobs[scope.row.job].subs[scope.row.growType] ? jobs[scope.row.job].subs[scope.row.growType] : '_:' + scope.row.growType"></span>
                    </template>
                </el-table-column>
                <el-table-column prop="maxHp" label="HP" width="80" />
                <el-table-column prop="maxMp" label="MP" width="80" />
                <el-table-column prop="phyAttack" label="物攻" width="80" />
                <el-table-column prop="phyDefense" label="物防" width="80" />
                <el-table-column prop="magAttack" label="魔攻" width="80" />
                <el-table-column prop="magDefense" label="魔防" width="80" />
                <el-table-column prop="moveSpeed" label="移速" width="80" />
                <el-table-column prop="attackSpeed" label="攻速" width="80" />
                <el-table-column prop="castSpeed" label="施速" width="80" />
                <el-table-column prop="hitRecovery" label="硬直" width="80" />
                <el-table-column prop="jump" label="跳跃力" width="80" />
                <el-table-column
                    fixed="right"
                    label="操作"
                    width="170">
                    <template #default="scope">
                        <el-button link type="primary" size="small" @click="toEdit(scope.row)">编辑</el-button>
                        <el-button link type="primary" size="small" @click="sendMail(scope.row.characNo, scope.row.characName)">发送邮件</el-button>
                    </template>
                </el-table-column>
            </el-table>
        </div>


        <div style="text-align: right; margin-top: 20px">
            <el-pagination
                background
                :page-sizes="[10, 20, 50, 100]"
                layout="total, sizes, prev, pager, next, jumper"
                @current-change="load"
                @size-change="sizeChange"
                :current-page="tableData.page"
                :page-size="tableData.length"
                :total="tableData.totalSize">
            </el-pagination>
        </div>


        <!-- 弹窗等 -->
        <div class="box">
            <send-email :option="sendMailOption"></send-email>
            <edit-charach :options="editOption" :on-save="onSave"></edit-charach>
        </div>

    </div>
</template>

<script>
import SendEmail from "./box/SendEmail.vue";
import api from "../../libs/api.js";
import EditCharach from "./box/EditCharach.vue";

export default {
    name: "MgrCharach",
    components: {EditCharach, SendEmail},
    data(){
        return {
            from: {
                name: '',
                account: '',
                job: '',
                superJob: false,
                minLev: '',
                maxLev: ''
            },
            loading: false,
            tableData: {
                list: [],
                page: 1,
                totalSize: 0,
                length: 10
            },
            jobs: [
              {
                name: '鬼剑士(男)',
                subs: {
                  '0': '未转职',
                  '1': '剑魂', '2': '鬼泣', '3': '狂战士' , '4': '阿修罗',
                  '17': '剑圣', '18': '弑魂', '19': '狱血魔神', '20': '大暗黑天'
                }
              },{
                name: '格斗家(女)',
                subs: {
                  '0': '未转职',
                  '1': '气功师', '2': '散打', '3': '街霸', '4': '柔道家',
                  '17': '百花缭乱', '18': '武神', '19': '毒王', '20': '暴风眼'
                }
              },{
                name: '神枪手(男)',
                subs: {
                  '0': '未转职',
                  '1': '漫游枪手', '2': '枪炮师', '3': '机械师', '4': '弹药专家',
                  '17': '枪神', '18': '狂暴者', '19': '机械战神', '20': '大将军'
                }
              },{
                name: '魔法师(女)',
                subs: {
                  '0': '未转职',
                  '1': '元素师', '2': '召唤师', '3': '战斗法师', '4': '魔道学者',
                  '17': '大魔导师', '18': '月之女皇', '19': '贝亚娜斗神', '20': '魔术师'
                }
              },{
                name: '圣职者(男)',
                subs: {
                  '0': '未转职',
                  '1': '圣骑士', '2': '蓝拳圣使', '3': '驱魔师', '4': '复仇者',
                  '17': '天启者', '18': '神之手', '19': '龙斗士', '20': '末日守护者'
                }
              },{
                name: '神枪手(女)',
                subs: {
                  '0': '未转职',
                  '1': '漫游枪手', '2': '枪炮师', '3': '机械师', '4': '弹药专家',
                  '17': '沾血蔷薇', '18': '重炮掌控者', '19': '机械之心', '20': '战争女神'
                }
              },{
                name: '暗夜使者(女)',
                subs: {
                  '0': '未转职',
                  '1': '刺客', '2': '死灵术士',
                  '17': '银月', '18': '灵魂收割者'
                }
              },{
                name: '格斗家(男)',
                subs: {
                  '0': '未转职',
                  '1': '气功师', '2': '散打', '3': '街霸', '4': '柔道家',
                  '17': '狂虎帝', '18': '武极', '19': '千手罗汉', '20': '风林火山'
                }
              },{
                name: '魔法师(男)',
                subs: {
                  '0': '未转职',
                  '1': '元素师', '2': '冰结者',
                  '17': '元素爆破师', '18': '冰冻之心'
                }
              },{
                name: '黑暗武士(男)',
                subs: {
                  '0': '未转职',
                  '1': '黑暗武士',
                  '17': '自我觉醒'
                }
              }],
            sendMailOption: {
                open: false,
                id: 0
            },
            ser: api,
            editOption: {
              show: false,
              form: undefined
            }
        }
    },
    methods: {
        onSave(){
          this.load()
        },
        toEdit(item){
          this.editOption.show = true
          this.editOption.form = JSON.parse(JSON.stringify(item));
          debugger
          this.editOption.form.growType = this.jobs[item.job].subs[item.growType] ?
              this.jobs[item.job].subs[item.growType] : '_:' + item.growType
          this.editOption.form.job = this.jobs[item.job].name;
        },
        sizeChange(size){
            this.tableData.length = size;
            this.tableData.page = 1;
            this.load();
        },
        load(page){
            if (page >= 1){
                this.tableData.page = page;
            }
            this.loading = true;
            this.ser.get("charac?page=" + this.tableData.page + "&pageSize=" + this.tableData.length
                + "&name=" + this.from.name + "&account=" + this.from.account + "&job=" + this.from.job
                + "&minLev=" + this.from.minLev + "&maxLev=" + this.from.maxLev).then(res => {
                this.tableData = res.data;
            }).finally(() => {
                this.loading = false;
            })
        },
        sendMail(id, name){
            this.sendMailOption.id = id;
            this.sendMailOption.open = true;
            this.sendMailOption.receiveCharacName = name
        }
    },
    mounted() {
        this.load();
    }
}
</script>

<style scoped>

</style>