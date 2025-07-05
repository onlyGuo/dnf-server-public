<template>
    <el-container>
        <el-header style="padding: 0">
            <div style="background-color: #2c3e50; height: 100%;">
                <h1 style="float: left; color: white;height: 100%; line-height: 60px; padding: 0; margin: 0 10px">后台管理</h1>
                <el-dropdown style="height: 100%; color: white; float: right; margin-right: 20px; cursor: pointer;" trigger="click">
                    <div class="el-dropdown-link" style="height: 100%; line-height: 60px; font-size: 18px;">
                        Admin<i class="el-icon-arrow-down el-icon--right"></i>
                    </div>
                    <el-dropdown-menu slot="dropdown">
                        <el-dropdown-item>退出</el-dropdown-item>
                    </el-dropdown-menu>
                </el-dropdown>
            </div>
        </el-header>
        <el-container>
            <el-aside class="left-nav">
                <el-menu style="height: 100%"
                         router
                         unique-opened
                         :default-active="defaultPath"
                         class="el-menu-vertical-demo"
                         background-color="#545c64"
                         text-color="#fff"
                         active-text-color="#ffd04b">
                    <el-scrollbar style="height: 100%;">
                        <el-sub-menu index="1">
                            <template #title>
                                <el-icon><user /></el-icon>
                                <span>玩家管理</span>
                            </template>
                            <el-menu-item index="/manager/account">账号管理</el-menu-item>
                            <el-menu-item index="/manager/charac">角色管理</el-menu-item>
                        </el-sub-menu>
                        <el-sub-menu index="2">
                            <template #title>
                                <el-icon><notebook /></el-icon>
                                <span>内容管理</span>
                            </template>
                            <el-menu-item index="/manager/notice">公告管理</el-menu-item>
                            <el-menu-item index="/manager/news">新闻管理</el-menu-item>
                        </el-sub-menu>
                        <el-menu-item index="/manager/action">
                            <el-icon><magic-stick /></el-icon>
                            <span slot="title">活动管理</span>
                        </el-menu-item>
                        <el-sub-menu index="3">
                            <template #title>
                                <el-icon><shopping-cart /></el-icon>
                                <span>充值管理</span>
                            </template>
                            <el-menu-item index="/manager/keys">卡密管理</el-menu-item>
<!--                            <el-menu-item index="/manager/k-logs">充值历史</el-menu-item>-->
                        </el-sub-menu>
                        <el-menu-item index="/manager/databases">
                            <el-icon><data-board /></el-icon>
                           <span slot="title">数据库管理</span>
                        </el-menu-item>
                    </el-scrollbar>
                </el-menu>
            </el-aside>
            <el-main>
                <router-view/>
            </el-main>
        </el-container>

    </el-container>
</template>

<script>
import router from "../router/index.js";
import {DataBoard, MagicStick, Notebook, ShoppingCart, User} from "@element-plus/icons-vue";

export default {
    name: "Manager",
    components: {DataBoard, ShoppingCart, MagicStick, Notebook, User, router},
    data() {
        return {
            activeIndex: '',
            defaultPath: router.currentRoute.value.path
        }
    }
}
</script>

<style lang="less">
    .left-nav .el-scrollbar__wrap{
        overflow-x: hidden;
    }
</style>