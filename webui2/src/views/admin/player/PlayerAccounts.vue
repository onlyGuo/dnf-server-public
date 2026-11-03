<!-- 文件: `webui2/src/views/admin/player/PlayerAccounts.vue` -->
<script setup lang="ts">
import {onMounted, ref} from "vue";
import type {AccountSearchForm} from "../../../api/entitys/AccountSearchForm";
import type {PageResult} from "../../../api/entitys/PageResult";
import type {RechargeOption} from "../../../api/entitys/RechargeOption";
import Request from "../../../api/Request";
import {Message} from "@arco-design/web-vue";

const loading = ref(false);

const searchForm = ref<AccountSearchForm>({
  pageNum: 1,
  pageSize: 10
})
const pageResult = ref<PageResult>({
  page: 1,
  totalPageSize: 10,
  totalSize: 0,
  list: []
});
const rechargeOption = ref<RechargeOption>({
  uid: 0,
  accountname: "",
  open: false,
  cera: 0,
  thisCera: 0,
});
const windowHeight = ref(window.innerHeight - 250)


const search = (resetPage = false) => {
  if (resetPage) {
    searchForm.value.pageNum = 1;
  }

  console.log('Search form:', searchForm.value);
  let url = `api/v1/account?page=${searchForm.value.pageNum}&pageSize=${searchForm.value.pageSize}`;
  url += searchForm.value.account ? `&account=${searchForm.value.account}` : '';
  url += searchForm.value.loginStatus !== undefined && searchForm.value.loginStatus !== '' ?
      `&loginStatus=${searchForm.value.loginStatus}` : '';
  url += searchForm.value.lastLoginTime && searchForm.value.lastLoginTime.length === 2 ?
      `&lastLoginDate=${searchForm.value.lastLoginTime[0]}&lastLoginDateEnd=${searchForm.value.lastLoginTime[1]}` : '';

  loading.value = true;
  Request.get(url).then((response) => {
    console.log('Search response:', response);
    pageResult.value = response.data;

    // 兼容后端返回当前页字段
    if (pageResult.value && typeof (pageResult.value as any).page === "number") {
      searchForm.value.pageNum = (pageResult.value as any).page;
    }
  }).catch((e: any) => {
    Message.error(e?.message || "查询失败");
  }).finally(() => {
    loading.value = false;
  });
}

const onPageChange = (page: number) => {
  searchForm.value.pageNum = page;
  search(false);
};

const onPageSizeChange = (pageSize: number) => {
  searchForm.value.pageSize = pageSize;
  search(true);
};

/**
 * 充值：打开弹窗并拉取当前余额
 * 说明：沿用旧版接口语义：GET account/{uid} 返回包含 cera 字段
 * 若你后端已统一到 api/v1，请自行替换 URL
 */
const openRecharge = async (uid: number, accountname: string) => {
  rechargeOption.value.uid = uid;
  rechargeOption.value.accountname = accountname;
  rechargeOption.value.cera = 0;
  rechargeOption.value.thisCera = 0;
  rechargeOption.value.open = true;

  try {
    const res = await Request.get(`api/v1/account/${uid}`);
    rechargeOption.value.thisCera = Number(res.data?.cera ?? 0);
  } catch {
    // 拉余额失败也允许继续输入充值
  }
};

/**
 * 充值：提交
 * 说明：沿用旧版接口语义：POST recharge，body=RechargeOption
 * 若你后端已统一到 api/v1，请自行替换 URL
 */
const submitRecharge = async () => {
  if (!rechargeOption.value.uid) return;

  const amount = Number(rechargeOption.value.cera ?? 0);
  if (!Number.isFinite(amount) || amount <= 0) {
    Message.error("请输入大于 0 的充值数额");
    return;
  }

  try {
    await Request.post(`api/v1/recharge`, rechargeOption.value);
    Message.success("充值成功");
    rechargeOption.value.open = false;
    search(false);
  } catch (e: any) {
    Message.error(e?.message || "充值失败");
  }
};


onMounted(() => {
  window.addEventListener('resize', () => {
    windowHeight.value = window.innerHeight - 250;
  });
  search();
});
</script>

<template>
  <div class="account-manager">
    <a-card>
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="账号">
          <a-input placeholder="搜索玩家账号" allow-clear v-model="searchForm.account"/>
        </a-form-item>
        <a-form-item label="状态">
          <a-select placeholder="全部状态" style="width: 150px;" allow-clear v-model="searchForm.loginStatus">
            <a-option label="全部状态" value="" />
            <a-option label="在线" value="true" />
            <a-option label="离线" value="false" />
          </a-select>
        </a-form-item>
        <a-form-item label="最后登录时间" >
          <a-range-picker v-model="searchForm.lastLoginTime" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="search(true)">搜索</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 账号列表 -->
    <a-table
        scrollbar
        :scroll="{ y: windowHeight }"
        style="margin-top: 10px;"
        :data="pageResult.list"
        :loading="loading"
        :pagination="false"
    >
      <template #empty>
        <div style="text-align: center; padding: 20px;">
          暂无数据
        </div>
      </template>
      <template #columns>
        <a-table-column title="账号" data-index="accountname" />
        <a-table-column title="在线状态" data-index="loginStatus">
          <template #cell="{ record }">
            <a-tag :color="record.loginStatus ? 'green' : 'gray'">
              {{ record.loginStatus ? '在线' : '离线' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="用户身份" data-index="admin">
          <template #cell="{ record }">
            <a-tag :color="record.admin === 2 ? 'purple' : record.admin === 1 ? 'green' : 'blue'">
              {{ record.admin === 2 ? '超级管理员' : record.admin === 1 ? 'GM' : '玩家' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="频道号" data-index="channelNo">
          <template #cell="{ record }">
          <span>
            {{ record.loginStatus ? record.channelNo : '--' }}
          </span>
          </template>
        </a-table-column>
        <a-table-column title="最后登录时间" data-index="lastLoginDate">
          <template #cell="{ record }">
            {{ record.lastLoginDate ? record.lastLoginDate: '从未登录' }}
          </template>
        </a-table-column>
        <a-table-column title="操作">
          <template #cell="{ record }">
            <a-space>
              <a-button size="small" type="primary" @click="openRecharge(record.uid, record.accountname)">充值</a-button>
              <!--            <a-button size="small" status="warning" @click="{}"-->
              <!--            >封号/解封</a-button-->
              <!--            >-->
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 12px;">
      <a-pagination
          :current="searchForm.pageNum"
          :page-size="searchForm.pageSize"
          :total="pageResult.totalSize"
          show-total
          show-jumper
          show-page-size
          :page-size-options="[10, 20, 50, 100]"
          @change="onPageChange"
          @page-size-change="onPageSizeChange"
      />
    </div>

    <!-- 充值窗口 -->
    <a-modal
        v-model:visible="rechargeOption.open"
        :title="`为：${rechargeOption.accountname} 充值`"
        :mask-closable="false"
        @ok="submitRecharge"
        @cancel="rechargeOption.open = false"
    >
      <a-form :model="rechargeOption" layout="vertical">
        <a-form-item label="当前余额">
          <a-input :model-value="String(rechargeOption.thisCera)" disabled />
        </a-form-item>
        <a-form-item label="充值数额">
          <a-input-number v-model="rechargeOption.cera" :min="1" :max="9999999" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.account-manager{
  padding: 10px;
}
</style>
