<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { LauncherApi } from '../../../api/launcher';
import type { LauncherVersion } from '../../../api/entitys/LauncherVersion';

const loading = ref(false);
const list = ref<LauncherVersion[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);

const keyword = ref('');
const enabled = ref<number | undefined>(undefined);

const editVisible = ref(false);
const saving = ref(false);
const form = ref<LauncherVersion>({
  id: 0,
  version: '',
  downloadUrl: '',
  description: '',
  enabled: 1,
  forceUpdate: 0
});

const isEdit = computed(() => !!form.value.id);

const loadList = async () => {
  loading.value = true;
  try {
    const res = await LauncherApi.listVersions({
      keyword: keyword.value.trim() || undefined,
      enabled: enabled.value,
      page: page.value,
      pageSize: pageSize.value
    });
    list.value = res.data?.list ?? [];
    total.value = res.data?.totalSize ?? 0;
  } finally {
    loading.value = false;
  }
};

const openCreate = () => {
  form.value = { id: 0, version: '', downloadUrl: '', description: '', enabled: 1, forceUpdate: 0 };
  editVisible.value = true;
};

const openEdit = (row: LauncherVersion) => {
  form.value = { ...row };
  editVisible.value = true;
};

const save = async () => {
  saving.value = true;
  try {
    if (!form.value.version || !form.value.downloadUrl) {
      Message.warning('请填写版本号与下载地址');
      return;
    }
    if (isEdit.value) {
      await LauncherApi.updateVersion(form.value);
    } else {
      await LauncherApi.addVersion(form.value);
    }
    Message.success('保存成功');
    editVisible.value = false;
    await loadList();
  } finally {
    saving.value = false;
  }
};

const del = async (row: LauncherVersion) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定删除版本 ${row.version} 吗？`,
    onOk: async () => {
      await LauncherApi.deleteVersion(row.id);
      Message.success('已删除');
      await loadList();
    }
  });
};

const onSearch = () => {
  page.value = 1;
  loadList();
};

onMounted(loadList);
</script>

<template>
  <a-space direction="vertical" fill size="large">
    <a-alert style="margin: 24px 24px 0 24px; width: calc(100% - 48px);"
        ref="alertEl"
        type="warning"
        :show-icon="true"
        class="pvf-alert"
    >
      <template #title>注意</template>
      登录器会根据版本号拉取合适的更新列表, 自动依次更新, 因此已经发布不建议删除, 否则会造成过久的客户端丢失版本, 若需撤销版本改动, 建议使用新增的方式上传已撤销改动的版本, 而不是删除历史版本, 因为客户端不会同步已删除/不存在的版本。
    </a-alert>

    <a-card style="margin: 0 24px">
      <a-grid :cols="24" :col-gap="12" :row-gap="12">
        <a-grid-item :span="8">
          <a-input
            v-model="keyword"
            allow-clear
            placeholder="搜索版本号"
            @press-enter="onSearch"
          />
        </a-grid-item>
        <a-grid-item :span="6">
          <a-select v-model="enabled" placeholder="启用状态" allow-clear @change="onSearch">
            <a-option :value="1">启用</a-option>
            <a-option :value="0">禁用</a-option>
          </a-select>
        </a-grid-item>
        <a-grid-item :span="10" style="text-align: right">
          <a-space>
            <a-button @click="onSearch" type="primary">查询</a-button>
          </a-space>
        </a-grid-item>
      </a-grid>
    </a-card>

    <a-card title="版本列表" style="margin: 0 24px">
      <template #extra>
        <a-button type="primary" @click="openCreate">新增</a-button>
      </template>

      <a-table :data="list" :loading="loading" :pagination="false" row-key="id">
        <template #empty>
          <div style="text-align: center; padding: 20px;">
            暂无数据
          </div>
        </template>
        <template #columns>
          <a-table-column title="版本" data-index="version" :width="140" />
          <a-table-column title="下载地址" data-index="downloadUrl" :ellipsis="true" />
          <a-table-column title="强制" :width="90">
            <template #cell="{ record }">
              <a-tag :color="record.forceUpdate === 1 ? 'red' : 'gray'">{{ record.forceUpdate === 1 ? '是' : '否' }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="启用" :width="90">
            <template #cell="{ record }">
              <a-tag :color="record.enabled === 1 ? 'green' : 'gray'">{{ record.enabled === 1 ? '启用' : '禁用' }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="描述" :ellipsis="true">
            <template #cell="{ record }">
              {{ record.description || '-' }}
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="180" align="right">
            <template #cell="{ record }">
              <a-space>
                <a-button size="mini" @click="openEdit(record)">编辑</a-button>
                <a-button size="mini" status="danger" @click="del(record)">删除</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>

      <a-divider style="margin: 12px 0" />

      <a-space style="width: 100%; justify-content: flex-end" >
        <a-pagination
          :current="page"
          :page-size="pageSize"
          :total="total"
          :show-jumper="true"
          @change="(p:number) => { page = p; loadList(); }"
          @page-size-change="(ps:number) => { pageSize = ps; page = 1; loadList(); }"
        />
      </a-space>
    </a-card>

    <a-modal v-model:visible="editVisible" :title="isEdit ? '编辑版本' : '新增版本'" :ok-loading="saving" @ok="save">
      <a-form layout="vertical" :model="form">
        <a-form-item label="版本号">
          <a-input v-model="form.version" placeholder="例如 1.0.1" />
        </a-form-item>
        <a-form-item label="更新包下载地址">
          <a-input v-model="form.downloadUrl" placeholder="https://.../client_update_xxx.zip" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model="form.description" :auto-size="{ minRows: 2, maxRows: 6 }" />
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model="form.enabled" :checked-value="1" :unchecked-value="0" />
        </a-form-item>
        <a-form-item label="强制更新">
          <a-switch v-model="form.forceUpdate" :checked-value="1" :unchecked-value="0" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-space>
</template>

<style scoped>
/* 使用框架默认样式 */
</style>
