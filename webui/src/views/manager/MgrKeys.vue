<template>
    <div>
        <el-breadcrumb separator="/" style="margin-bottom: 30px">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>充值管理</el-breadcrumb-item>
            <el-breadcrumb-item>卡密列表</el-breadcrumb-item>
        </el-breadcrumb>


        <!-- 搜索条件 -->
        <div style="margin: 20px auto;">
            <el-form :inline="true" :model="from" class="demo-form-inline">
                <el-form-item label="卡密">
                    <el-input v-model="from.content" placeholder="模糊搜索"></el-input>
                </el-form-item>
                <el-form-item label="类型">
                    <el-select v-model="from.type" placeholder="卡密类型">
                        <el-option label="全部类型" value=""></el-option>
                        <el-option label="点券" value="0"></el-option>
                        <el-option label="装备CDK" value="1"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item label="是否使用">
                    <el-select v-model="from.status" placeholder="是否使用">
                        <el-option label="全部状态" value=""></el-option>
                        <el-option label="未使用" value="0"></el-option>
                        <el-option label="已使用" value="1"></el-option>
                    </el-select>
                </el-form-item>

                <el-form-item>
                    <el-button type="primary" @click="load">查询</el-button>
                    <el-button type="success" @click="showCreate = true">制卡</el-button>
                </el-form-item>
            </el-form>
        </div>

        <el-table
            :data="tableData.list"
            border
            v-loading="loading"
            style="width: 100%">
            <el-table-column fixed prop="id" label="ID" min-width="50" />
            <el-table-column prop="content" label="卡密" min-width="320"/>
            <el-table-column prop="type" label="类型" width="70">
                <template slot-scope="scope">
                    <div style="text-align: center;">
                        <el-tag v-text="scope.row.type === 0 ? '点券' : '装备'"></el-tag>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="face" label="面值" min-width="120" >
                <template slot-scope="scope">
                    <span v-text="scope.row.type === 0 ? scope.row.face : scope.row.faceName"></span>
                </template>
            </el-table-column>
            <el-table-column prop="status" label="是否使用" width="80">
                <template slot-scope="scope">
                    <div style="text-align: center;">
                        <el-tag :type="!scope.row.status ? 'success' : 'info'"
                                v-text="!scope.row.status ? '未使用' : '已使用'"></el-tag>
                    </div>
                </template>
            </el-table-column>
            <el-table-column prop="useAccount" label="使用账号" min-width="150" />
            <el-table-column prop="useTime" label="使用时间" width="160" />
            <el-table-column
                fixed="right"
                label="操作"
                width="150">
                <template slot-scope="scope">
                    <el-button type="text" size="small" @click="del(scope.row.id)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>

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

        <div>
            <el-dialog
                title="制作卡密"
                :visible.sync="showCreate"
                v-loading="loadingCreateKey"
                width="50%">
                <div>
                    <el-form ref="form" :model="createFrom" label-width="80px">
                        <el-form-item label="类型">
                            <el-select v-model="createFrom.type" placeholder="卡密类型" style="width: calc(100% - 200px);">
                                <el-option label="点券" :value="0"></el-option>
                                <el-option label="装备CDK" :value="1"></el-option>
                            </el-select>
                            数量
                            <el-input-number v-model="createFrom.count" :min="0" :max="100" label="卡密数量" placeholder="卡密数量" style="width: 160px"></el-input-number>
                        </el-form-item>
                        <el-form-item label="面值" v-if="createFrom.type === 0">
                            <el-input v-model="createFrom.face" placeholder="输入点券金额"></el-input>
                        </el-form-item>
                        <el-form-item label="装备" v-if="createFrom.type === 1">
                            <el-select style="width: calc(100%)"
                                       v-model="createFrom.face"
                                       filterable
                                       remote
                                       placeholder="请输入关键词"
                                       :remote-method="searchItem"
                                       :loading="itemSearchLoading">
                                <el-option
                                    v-for="item in itemOptions"
                                    :key="item.code"
                                    :label="item.name"
                                    :value="item.code">
                                </el-option>
                            </el-select>
                        </el-form-item>
                    </el-form>
                </div>
                <span slot="footer" class="dialog-footer">
                <el-button @click="showCreate = false">取 消</el-button>
                <el-button type="primary" @click="createKey">确 定</el-button>
            </span>
            </el-dialog>

        </div>
    </div>
</template>

<script>
let ITEM_LIST = require('../../assets/itemList.json');
export default {
    name: "MgrKeys",
    data() {
        return {
            tableData: {
                list: [],
                page: 1,
                length: 10
            },
            loading: false,
            loadingCreateKey: false,
            showCreate: false,
            itemSearchLoading: false,
            from: {
                type: '',
                status: '',
                content: ''
            },
            createFrom: {
                type: 0,
                count: 1,
                face: null,
                faceName: null
            },
            itemOptions: []
        }
    },
    methods:{
        del(id){
            console.log(id);
        },
        load(){
            this.loading = true
            this.ser.get("keys?page=" + this.tableData.page + "&pageSize=" + this.tableData.length
                + "&type=" + this.from.type + "&status=" + this.from.status
                + "&content=" + this.from.content).then(res => {
                    this.tableData = res.data;
            }).finally(() => {
                this.loading = false;
            })
        },
        createKey(){
            this.loadingCreateKey = true
            this.createFrom.faceName = this.createFrom.type == 0 ? '点券' : this.itemOptions.find(val=>val.code === this.createFrom.face).name
            this.ser.post("keys", this.createFrom).then(() => {
                this.$message.success("制卡成功");
                this.showCreate = false;
                this.page = 1;
                this.load();
            }).finally(() => {
                this.loadingCreateKey = false;
            })
        },
        sizeChange(size){
            this.tableData.length = size;
            this.tableData.page = 1;
            this.load();
        },
        /**
         * 远程搜索物品列表
         * @param query
         */
        searchItem(query){
            this.itemSearchLoading = true;
            this.itemOptions = [];
            setTimeout(() => {
                let count = 0;
                for (let i in ITEM_LIST){
                    if (count === 10){
                        break;
                    }
                    if(ITEM_LIST[i].name.indexOf(query) !== -1){
                        this.itemOptions.push(ITEM_LIST[i]);
                        count ++;
                    }
                }
                this.itemSearchLoading = false;
            });
        }
    },
    mounted() {
        this.load();
    }
}
</script>

<style scoped>

</style>
