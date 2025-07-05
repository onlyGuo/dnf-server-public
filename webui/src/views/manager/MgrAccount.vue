<template>
    <div>
      <el-breadcrumb separator="/" style="margin-bottom: 30px">
        <el-breadcrumb-item>首页</el-breadcrumb-item>
        <el-breadcrumb-item>玩家管理</el-breadcrumb-item>
        <el-breadcrumb-item>账号列表</el-breadcrumb-item>
      </el-breadcrumb>

      <div>
        <!-- 搜索条件 -->
        <div style="margin: 20px auto;">
          <el-form :inline="true" :model="from" class="demo-form-inline">
            <el-form-item label="账号">
              <el-input v-model="from.account" placeholder="模糊搜索"></el-input>
            </el-form-item>

            <el-form-item label="状态" style="width: 150px">
              <el-select v-model="from.loginStatus" placeholder="在线状态">
                <el-option label="全部状态" value=""></el-option>
                <el-option label="在线" value="true"></el-option>
                <el-option label="离线" value="false"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="最后登录时间">
              <el-date-picker
                  v-model="from.lastLoginDate"
                  type="datetime"
                  value-format="yyyy-MM-dd HH:mm:ss"
                  placeholder="选择日期时间">
              </el-date-picker>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="load">查询</el-button>
            </el-form-item>
          </el-form>
        </div>

        <el-table
            :data="tableData.list"
            border
            v-loading="loading"
            stripe
            style="width: 100%">
          <el-table-column fixed prop="uid" label="ID" min-width="50" />
          <el-table-column prop="accountname" label="账号" min-width="120"/>
          <el-table-column prop="loginStatus" label="在线状态" min-width="120">
            <template #default="scope">
              <div style="text-align: center;">
                <el-tag :type="scope.row.loginStatus ? 'success' : 'info'"
                        v-text="scope.row.loginStatus ? '在线' : '离线'"></el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="parentUid" label="用户身份" min-width="120">
            <template #default="scope">
              <el-tag :type="scope.row.parentUid == 0 ? 'success' : 'info'"
                      v-text="scope.row.parentUid == 0 ? 'GM' : '普通用户'"></el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="channelNo" label="频道号" min-width="120" >
            <template #default="scope">
              <span v-text="scope.row.loginStatus ? scope.row.channelNo : '--'"></span>
            </template>
          </el-table-column>
          <el-table-column prop="lastLoginDate" label="最后登录时间" min-width="160" />
          <el-table-column prop="loginIp" label="最后登录IP" min-width="150" />
          <el-table-column
              fixed="right"
              label="操作"
              width="170">
            <template #default="scope">
              <el-button link type="primary" size="small" @click="openRecharge(scope.row.uid, scope.row.accountname)">充值</el-button>
              <el-button link type="primary" size="small">封号/解封</el-button>
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

      <el-dialog
          :title="'为:' + rechargeOption.accountname + '充值'"
          :model-value="rechargeOption.open"
          width="50%">
        <div>
          <el-form ref="form" :model="rechargeOption" label-width="80px">
            <el-form-item label="当前余额">
              <el-input v-model="rechargeOption.thisCrea" disabled></el-input>
            </el-form-item>
            <el-form-item label="充值数额">
              <el-input-number v-model="rechargeOption.cera" :max="9999999" type="number"></el-input-number>
            </el-form-item>
          </el-form>
        </div>
        <span slot="footer" class="dialog-footer">
                <el-button @click="rechargeOption.open = false">取 消</el-button>
                <el-button type="primary" @click="onChange">确 定</el-button>
            </span>
      </el-dialog>
    </div>
</template>

<script>
import api from "../../libs/api.js";

export default {
    name: "MgrAccount",
  data(){
        return{
            loading: false,
            from: {
                account: '',
                loginStatus: '',
                lastLoginDate: ''
            },
            tableData: {
                page: 1,
                length: 10,
                totalSize: 0,
                list: []
            },
            rechargeOption: {
                uid: 0,
                accountname: '',
                open: false,
                cera: 0,
                thisCrea: 0,
            },
            ser: api
        }
    },
    methods: {
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
            this.ser.get("account?page=" + this.tableData.page + "&pageSize=" + this.tableData.length
                + "&account=" + this.from.account + "&loginStatus=" + this.from.loginStatus
                + (this.from.lastLoginDate ? "&lastLoginDate=" + this.from.lastLoginDate : '')).then(res => {
                this.tableData = res.data;
            }).finally(() => {
                this.loading = false;
            })
        },
        openRecharge(uid, accountname){
            this.rechargeOption.uid = uid;
            this.rechargeOption.accountname = accountname;
            this.rechargeOption.open = true;
            this.ser.get("account/" + uid).then(res => {
                console.log(res.data)
                this.rechargeOption.thisCrea = res.data.cera;
            });
        },
        onChange(){
            this.ser.post("recharge", this.rechargeOption).then(() => {
                this.$message.success("充值成功");
                this.rechargeOption.open = false;
            })
        }
    },
    mounted() {
        this.load();
    }
}
</script>

<style scoped>

</style>