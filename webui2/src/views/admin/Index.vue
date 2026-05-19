<script setup lang="ts">

import {nextTick, onMounted, ref} from "vue";
import RecursiveMenuItem from "../../components/RecursiveMenuItem.vue";
import router from "../../router";
import {ApiGlobalConfig} from "../../api/ApiGlobalConfig.ts";
import Request from "../../api/Request.ts";

const collapsed = ref(false);
const onCollapse = () => {
  collapsed.value = !collapsed.value;
};
const menus = ref([] as any[]);
const breadcrumbList = ref<string[]>(['系统后台', '首页']);
const selectedKeys = ref<string[]>(['/dashboard']);
const openKeys = ref<string[]>([]);
const userInfo = ref<any>(null);
const historyPages = ref<any[]>([{
  path: '/dashboard',
  meta: { title: '首页', icon: 'home' }
}]);

menus.value = [
  {
    path: '/dashboard',
    meta: { title: '首页', icon: 'home' }
  },
  {
    path: '/player',
    meta: { title: '玩家管理', icon: 'home' },
    children: [
      {
        path: 'accounts',
        meta: { title: '账号管理', icon: 'home' },
      },
      {
        path: 'roles',
        meta: { title: '角色管理', icon: 'home' },
      }
    ]
  },
  {
    path: '/pvf',
    meta: { title: 'PVF管理', icon: 'home' },
    children: [
      {
        path: 'manager',
        meta: { title: 'PVF管理', icon: 'home' },
      }
    ]
  },
  {
    path: '/login',
    meta: { title: '登录器管理', icon: 'home' },
    children: [
      {
        path: 'config',
        meta: { title: '登录器配置', icon: 'home' },
      },
      {
        path: 'versions',
        meta: { title: '版本管理', icon: 'home' },
      }
    ]
  },
  {
    path: '/assist',
    meta: { title: '客户端管理', icon: 'home' }
  }
];

onMounted(() => {
  nextTick(() => {
    console.log('Menus:', menus.value);
    // 初始化面包屑导航
    let path = router.currentRoute.value.fullPath;
    if (!path || path.trim() === '/'){
      path = window.location.pathname;
    }
    onClickMenuItem(path.slice(6));
  });
});


const onClickMenuItem = (item: any, _ev?: Event) => {
  const tempBreadcrumbList = ['系统后台'] as string[];
  const itemObj = searchByFullPath(item, menus.value, tempBreadcrumbList);
  if (!itemObj){
    throw new Error(`Menu item not found for path: ${item}`);
  }
  const path = '/admin' + (item.startsWith('/') ? item : `/${item}`);
  breadcrumbList.value = tempBreadcrumbList;
  selectedKeys.value = [item];
  // 添加到历史记录标签页
  const exists = historyPages.value.find(page => page.path === item);
  if (!exists) {
    historyPages.value.push({
      path: item,
      meta: { title: itemObj.meta?.title || '未命名', icon: itemObj.meta?.icon || '' }
    });
  }
  if (router.currentRoute.value.fullPath !== path) {
    router.push({ path });
  }

  // 如果是嵌套路由，展开父级菜单（兼容 '/pvf/manager' 这种深层路径）
  const parts = item.split('/').filter(Boolean);
  const parentPath = parts.length > 1 ? `/${parts[0]}` : '';
  if (parentPath) {
    const parentItem = menus.value.find(menu => menu.path === parentPath);
    if (parentItem) {
      openKeys.value = [parentPath];
    }
  }
};

const searchByFullPath = (path: string, routes: any[], tempBreadcrumbList: string[]): any | null => {
  if (!tempBreadcrumbList){
    tempBreadcrumbList = ['系统后台'];
  }
  for (let route of routes) {
    const fullPath = route.fullPath || route.path;
    if (fullPath === path) {
      // 添加当前路由标题到面包屑列表
      tempBreadcrumbList.push(route.meta?.title || '');
      return route;
    }
    if (route.children && route.children.length > 0) {
      const found = searchByFullPath(path, route.children, tempBreadcrumbList);
      if (found) {
        // 添加当前路由标题到面包屑列表
        // tempBreadcrumbList.unshift(route.meta?.title || '');
        tempBreadcrumbList.splice(1, 0, route.meta?.title || '');
        return found;
      }
    }
  }
  return null;
};

const imgBaseUrl = ApiGlobalConfig.imageViewer.baseURL



const onDeleteTab = (tabKey: string) => {
  const index = historyPages.value.findIndex(page => page.path === tabKey);
  if (index !== -1) {
    historyPages.value.splice(index, 1);
    // 如果关闭的是当前激活的标签页，切换到最后一个标签页
    if (tabKey === selectedKeys.value[0]) {
      const lastPage = historyPages.value[historyPages.value.length - 1];
      if (lastPage) {
        onClickMenuItem(lastPage.path);
      }
    }
  }
};

const logout = () => {
  localStorage.removeItem('token')
  router.replace('/login')
}

// TODO 临时先随便请求个接口，触发后端验证token逻辑，获取用户信息
Request.get('api/v1/account?page=1&pageSize=1')
</script>

<template>
<div class="admin-page">
  <a-layout class="admin-layout">
    <a-layout-sider
        hide-trigger
        collapsible
        :collapsed="collapsed"
        theme="dark"
    >
      <a-menu class="left-nav"
          :selected-keys="selectedKeys"
          v-model:open-keys="openKeys"
          :style="{ width: '100%' }"
          theme="dark"
          @menuItemClick="onClickMenuItem"
      >

        <a-menu-item key="0" :style="{ padding: 0}" disabled>
          <div class="logo">
            <img src="../../assets/images/icon.png" alt="Logo" />
            <span v-if="!collapsed">
              DNF-Admin
            </span>
          </div>
        </a-menu-item>
        <recursive-menu-item
            v-for="route in menus"
            :key="route.path"
            :item="route"
        />
      </a-menu>
    </a-layout-sider>
    <a-layout class="layout-right">
      <a-layout-header class="layout-header">
        <div class="toggle-button" @click="onCollapse">
          <icon-menu-unfold v-if="collapsed" />
          <icon-menu-fold v-else />
        </div>
        <div class="breadcrumb-container">
          <a-breadcrumb :style="{ margin: '16px 0' }">
            <a-breadcrumb-item v-for="item in breadcrumbList">{{item}}</a-breadcrumb-item>
          </a-breadcrumb>
        </div>
        <div class="right">
          <a-dropdown trigger="hover">
            <div class="right-user-info">
              <div class="user-info-left">
                <a-avatar v-if="userInfo && userInfo.avatar" :size="40">
                  <!-- 环境变量 -->
                  <img
                      alt="avatar"
                      :src="imgBaseUrl + '/' + userInfo.avatar"
                  />
                </a-avatar>
                <a-avatar v-else-if="userInfo" :size="40">
                  {{userInfo.nickName ? userInfo.nickName : userInfo.userName}}
                </a-avatar>
                <a-avatar v-else :size="40">
                  <icon-user />
                </a-avatar>
              </div>
              <div class="user-info-right" style="color: white; margin-left: 8px;">
                <div>{{userInfo ? (userInfo.nickName ? userInfo.nickName : userInfo.userName) : 'GM管理员'}}</div>
                <div style="font-size: 12px;" v-if="userInfo">{{userInfo.email ? userInfo.email : (userInfo.phonenumber ? userInfo.phonenumber : '')}}</div>
              </div>
            </div>
            <template #content>
              <a-doption>
                <icon-user style="margin-right: 10px"/>个人信息
              </a-doption>
              <a-doption>
                <icon-lock style="margin-right: 10px"/>修改密码
              </a-doption>
              <a-doption @click="logout">
                <icon-poweroff style="margin-right: 10px"/>退出登录
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>
      <a-layout-content class="layout-content">
        <a-tabs class="layout-tabs" hide-content closable editable @delete="onDeleteTab" v-model:active-key="selectedKeys[0]" @change="(key: string | number, _ev: Event) => onClickMenuItem(String(key))">
          <a-tab-pane v-for="page in historyPages" :key="page.path" :title="page.meta.title" :closable="page.path !== selectedKeys[0]">
          </a-tab-pane>
        </a-tabs>
        <!-- 动态路由视图 -->
        <div class="content-view">
          <a-scrollbar style="height: 100%;overflow: auto;">
            <router-view :key="router.currentRoute.value.fullPath" class="router-viewer"/>
          </a-scrollbar>
        </div>
      </a-layout-content>
<!--      <a-layout-footer>-->
<!--        Footer-->
<!--      </a-layout-footer>-->
    </a-layout>
  </a-layout>
</div>
</template>

<style scoped lang="less">
.admin-page{
  height: 100vh;
  .admin-layout {
    height: 100%;

    .logo{
      display: flex;
      align-items: center;
      justify-content: center;
      height: 64px;
      flex-direction: row;
      gap: 8px;
      img {
        width: 32px;
        height: 32px;
      }
      span {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        color: white;
        font-size: 20px;
        font-weight: bold;
        vertical-align: middle;
      }
    }
    .left-nav{
      /deep/.arco-menu-selected{
        background-color: #1677ff !important;
        border-radius: 6px;

        &.arco-menu-inline-header {
          background-color: transparent !important;
          color: white !important;
          font-weight: bold;
          * {
            color: white !important;
          }
        }

      }
    }

    .layout-right{
      height: 100%;
      display: flex;
      .layout-header{
        background: var(--color-menu-dark-bg);
        padding: 0 16px;
        display: flex;
        align-items: center;
        height: 55px;

        .toggle-button{
          font-size: 20px;
          cursor: pointer;
          color: white;

          &:hover{
            color: dodgerblue;
          }
        }

        .breadcrumb-container{
          flex: 1;
          margin-left: 16px;
          display: flex;
          justify-content: left;
          /deep/.arco-breadcrumb-item{
            color: #ccc!important;

            &:last-child {
              color: white!important;
            }
          }
        }

        .right{
          display: flex;
          align-items: center;
          .right-user-info{
            display: flex;
            align-items: center;
            .user-info-left {
              display: flex;
              align-items: center;
              justify-content: center;
            }
            .user-info-right {
              display: flex;
              flex-direction: column;
              justify-content: center;
            }
          }
        }
      }
      .layout-content{
        display: flex;
        flex-direction: column;
        flex: 1;
        overflow: hidden;

        .layout-tabs{
          /deep/.arco-tabs-content{
            padding-top: 0!important;
          }
        }

        .content-view{
          // 填充剩余高度
          flex: 1;
          overflow: auto;

          /deep/.arco-scrollbar{
            height: 100%;
            overflow: auto;
          }
        }
      }
    }
  }


}
</style>
