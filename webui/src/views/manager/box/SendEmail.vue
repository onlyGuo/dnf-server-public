<template>
    <div>
        <el-dialog
                :title="'发送邮件到:' + option.receiveCharacName"
                v-model="option.open"
                width="50%">
            <div>
                <el-form ref="form" :model="form" label-width="80px">
                    <el-form-item label="发件人">
                        <el-input v-model="form.sendCharacName" disabled></el-input>
                    </el-form-item>
                  <el-form-item label="金币数量">
                    <el-input-number style="width: 100%" v-model="form.gold" :min="0" :max="10000000000" label="金币数量"></el-input-number>
                  </el-form-item>
<!--                  <el-form-item label="邮件内容">-->
<!--                    <el-input v-model="form.message" rows="5" type="textarea" resize="none" placeholder="请输入邮件内容，可以是空"></el-input>-->
<!--                  </el-form-item>-->
<!--                  <el-form-item label="附件列表">-->
<!--                    <el-table border>-->
<!--                      <el-table-column label="名称"></el-table-column>-->
<!--                      <el-table-column label="数量"></el-table-column>-->
<!--                      <el-table-column label="强化等级"></el-table-column>-->
<!--                      <el-table-column label="锻造等级"></el-table-column>-->
<!--                      <el-table-column label="红字属性"></el-table-column>-->
<!--                      <el-table-column label="是否封装"></el-table-column>-->
<!--                      <el-table-column label="操作"></el-table-column>-->
<!--                    </el-table>-->
<!--                    <el-button style="width: 100%">+ 添加附件</el-button>-->
<!--                  </el-form-item>-->
                    <el-form-item label="物品名称">
                        <el-select style="width: calc(100% - 150px)"
                                v-model="form.itemId"
                                filterable
                                remote
                                placeholder="物品太多，请输入关键词搜索"
                                :remote-method="searchItem"
                                :loading="searchLoading">
                            <el-option
                                    v-for="item in itemOptions"
                                    :key="item.code"
                                    :label="item.name"
                                    :value="item.code">
                            </el-option>
                        </el-select>
                        <el-input-number style="width: 150px" v-model="form.addInfo" :min="1" :max="100000" label="数量"></el-input-number>
                    </el-form-item>

                  <el-row>
                    <el-col :span="12">
                      <el-form-item  label="强化等级">
                        <el-input-number style="width: 100%" v-model="form.upgrade" :min="0" :max="31" label="强化等级"></el-input-number>
                      </el-form-item>
                    </el-col>
                    <el-col :span="12">
                      <el-form-item label="锻造等级">
                        <el-input-number style="width: 100%" v-model="form.seperateUpgrade" :min="0" :max="31" label="锻造等级"></el-input-number>
                      </el-form-item>
                    </el-col>
                  </el-row>




                    <el-form-item label="红字属性">
                        <el-select v-model="form.amplifyOption" placeholder="请选择属性" style="width: calc(100% - 200px)">
                            <el-option label="体力" value="1"></el-option>
                            <el-option label="精神" value="2"></el-option>
                            <el-option label="力量" value="3"></el-option>
                            <el-option label="智力" value="4"></el-option>
                        </el-select>
                        <el-input-number v-model="form.amplifyValue" :min="0" :max="65535" label="加成数值" style="width: 200px"></el-input-number>
                    </el-form-item>

                    <el-form-item label="是否封装">
                        <el-switch v-model="form.sealFlag"></el-switch>
                        <span style="color: red; font-size: 12px; margin-left: 15px;">注意: 史诗装备 (即：所有金色名称的物品) 不可封装，否则引起游戏崩溃</span>
                    </el-form-item>
                </el-form>
            </div>
            <span slot="footer" class="dialog-footer">
                <el-button @click="option.open = false">取 消</el-button>
                <el-button type="primary" @click="onsubmit">确 定</el-button>
            </span>
        </el-dialog>
    </div>
</template>

<script>
// let ITEM_LIST = require('../../../assets/itemList.json');
import api from "../../../libs/api.js";

let ITEM_LIST = import('../../../assets/itemList.json')
ITEM_LIST.then((a) => {
  ITEM_LIST = a.default;
})
export default {
    name: "SendEmail",
    props: {
        option: Object,
    },
    data(){
        return {
            form: {
                sendCharacNo: 0,
                message: '',
                sendCharacName: 'DNF Manageer',
                receiveCharacNo: 0,
                itemId: null,
                addInfo: 1,
                upgrade: 0,
                seperateUpgrade: 0,
                sealFlag: false,
                amplifyOption: null,
                amplifyValue: 0,
                gold: 0
            },
            searchLoading: false,
            loading: false,
            itemOptions:[
            ],
          ser: api
        }
    },methods: {
        onsubmit(){
            this.form.receiveCharacNo = this.option.id;
            if (!this.form.itemId){
                this.$message.error("必须选择一个物品");
                return;
            }
            this.loading = true;
            if(!this.form){
              this.form.amplifyOption = 0;
            }
            this.ser.post("postal", this.form).then(() => {
                this.$message.success("发送成功, 重新选择角色即可查看邮件");
                this.form =  {
                    sendCharacNo: 0,
                    message: '',
                    sendCharacName: 'DNF Manageer',
                    receiveCharacNo: 0,
                    itemId: null,
                    addInfo: 1,
                    upgrade: 0,
                    seperateUpgrade: 0,
                    sealFlag: false,
                    amplifyOption: null,
                    amplifyValue: 0,
                    gold: 0
                };
                this.option.open = false;
            }).finally(() => {
                this.loading = false;
            })
        },
        /**
         * 远程搜索物品列表
         * @param query
         */
        searchItem(query){
            this.searchLoading = true;
            this.itemOptions = [];
            setTimeout(() => {
                let count = 0;
                for (let i in ITEM_LIST){
                    if (count === 100){
                        break;
                    }
                    if(ITEM_LIST[i].name.indexOf(query) !== -1){
                        this.itemOptions.push(ITEM_LIST[i]);
                        count ++;
                    }
                }
                this.searchLoading = false;
            });
        }
    }
}
</script>

<style scoped>

</style>