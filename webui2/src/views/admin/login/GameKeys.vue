<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import Request from '../../../api/Request';

type KeyStatus = {
  gameAvailable: boolean;
  canManage: boolean;
  available: boolean;
  matchesGame: boolean;
  source?: string;
  bits?: number;
  fingerprint?: string;
  message?: string;
};
type ChangeResult = {
  restarted: string[];
  failed: { process: string; message: string }[];
  runningCount: number;
};

const status = ref<KeyStatus | null>(null);
const loading = ref(false);
const changing = ref(false);
const uploadVisible = ref(false);
const privateFile = ref<File | null>(null);
const publicFile = ref<File | null>(null);
type KeyPairFiles = { privateKey: File; publicKey: File };

const load = async () => {
  loading.value = true;
  try {
    const res = await Request.get<KeyStatus>('/api/v1/admin/keys');
    status.value = res.data;
  } finally { loading.value = false; }
};

const apply = async (files?: KeyPairFiles) => {
  changing.value = true;
  try {
    let res;
    if (files) {
      const data = new FormData();
      data.append('privateKey', files.privateKey);
      data.append('publicKey', files.publicKey);
      res = await Request.post<ChangeResult>('/api/v1/admin/keys/upload', data, { timeout: 0 });
    } else {
      res = await Request.post<ChangeResult>('/api/v1/admin/keys/generate', null, { timeout: 0 });
    }
    if (res.data.failed.length) {
      Modal.warning({
        title: '密钥已更新，部分进程重启失败',
        content: `请检查：${res.data.failed.map(item => `${item.process}（${item.message}）`).join('；')}`
      });
    } else if (!res.data.runningCount) {
      Message.info('密钥已更新，当前没有运行中的游戏进程，下次启动时生效');
    } else {
      Message.success(`密钥已更新，已触发 ${res.data.restarted.length} 个游戏进程重启`);
    }
    await load();
  } catch {
    // The request interceptor displays the server's error.
  } finally { changing.value = false; }
};

const confirmChange = (files?: KeyPairFiles) => {
  Modal.confirm({
    title: files ? '替换游戏公私钥对' : '重新生成游戏公私钥对',
    content: '操作会同步管理平台和游戏服务的密钥，并重启正在运行的游戏频道，在线玩家会断开。请确认后继续。',
    okText: files ? '替换公私钥' : '生成并替换',
    cancelText: '取消',
    onOk: () => { uploadVisible.value = false; void apply(files); }
  });
};

const selectedFile = (event: Event, type: 'private' | 'public') => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (type === 'private') privateFile.value = null;
  else publicFile.value = null;
  if (!file) return;
  if (!file.size || file.size > 64 * 1024) {
    input.value = '';
    Message.warning('公钥和私钥文件各需非空且不超过 64 KB');
    return;
  }
  if (type === 'private') privateFile.value = file;
  else publicFile.value = file;
};

const openUpload = () => {
  privateFile.value = null;
  publicFile.value = null;
  uploadVisible.value = true;
};

const confirmUpload = () => {
  if (!privateFile.value || !publicFile.value) {
    Message.warning('请同时选择公钥和私钥文件');
    return;
  }
  confirmChange({ privateKey: privateFile.value, publicKey: publicFile.value });
};

onMounted(() => { void load(); });
</script>

<template>
  <div class="keys-page">
    <div class="keys-heading">
      <div>
        <h2>密钥管理</h2>
        <p>管理游戏登录使用的 RSA 密钥。</p>
      </div>
      <a-button :loading="loading" :disabled="changing" @click="load">刷新状态</a-button>
    </div>

    <a-card class="keys-card" title="当前密钥">
      <a-spin v-if="!status && loading" tip="正在读取密钥状态" />
      <a-alert v-else-if="!status" type="error" show-icon>密钥状态加载失败，请刷新重试。</a-alert>
      <template v-else>
        <div class="key-status">
          <div class="key-label">状态</div>
          <div>
            <a-tag v-if="!status.available" color="gray">未配置</a-tag>
            <a-tag v-else-if="!status.gameAvailable" color="blue">已读取私钥</a-tag>
            <a-tag v-else-if="status.matchesGame" color="green">与游戏公钥匹配</a-tag>
            <a-tag v-else color="orange">游戏密钥未同步</a-tag>
          </div>
          <div class="key-label">私钥来源</div><div class="key-value">{{ status.source || '未配置' }}</div>
          <div class="key-label">密钥长度</div><div>{{ status.bits ? `${status.bits} 位` : '—' }}</div>
          <div class="key-label">公钥指纹</div><div class="key-value fingerprint">{{ status.fingerprint || '—' }}</div>
        </div>
        <a-alert v-if="status.message" :type="status.available && !status.gameAvailable ? 'info' : 'warning'" show-icon class="key-message">{{ status.message }}</a-alert>
        <div class="key-actions">
          <a-button type="primary" :disabled="!status.canManage || changing" @click="openUpload">上传公私钥对</a-button>
          <a-button status="danger" :disabled="!status.canManage || changing" @click="confirmChange()">重新生成公私钥</a-button>
          <span v-if="changing" class="working"><a-spin :size="14" /> 正在更新密钥并重启游戏进程</span>
        </div>
        <p class="key-hint">支持匹配的 RSA 公私钥文件；私钥需未加密，重新生成使用 2048 位。私钥不会显示在页面上。</p>
      </template>
    </a-card>

    <a-modal v-model:visible="uploadVisible" title="上传公私钥对" :footer="false" unmount-on-close>
      <div class="upload-fields">
        <label><span>私钥文件</span><input type="file" accept=".pem,.key,.der" @change="selectedFile($event, 'private')" /></label>
        <label><span>公钥文件</span><input type="file" accept=".pem,.key,.der" @change="selectedFile($event, 'public')" /></label>
        <p class="key-hint">上传前会检查公钥和私钥是否匹配；不匹配时不会更改现有密钥。</p>
        <div class="upload-actions">
          <a-button @click="uploadVisible = false">取消</a-button>
          <a-button type="primary" :disabled="!privateFile || !publicFile" @click="confirmUpload">确认上传</a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<style scoped>
.keys-page { padding: 24px; max-width: 1020px; margin: 0 auto; }
.keys-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.keys-heading h2 { margin: 0 0 6px; font-size: 22px; }
.keys-heading p, .key-hint { margin: 0; color: var(--color-text-3); }
.keys-card { border-radius: 10px; }
.key-status { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 18px 14px; align-items: start; }
.key-label { color: var(--color-text-3); }
.key-value { overflow-wrap: anywhere; }
.fingerprint { font-family: ui-monospace, monospace; font-size: 12px; }
.key-message { margin-top: 22px; }
.key-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
.key-hint { font-size: 12px; margin-top: 12px; }
.working { display: inline-flex; align-items: center; gap: 6px; color: var(--color-text-3); font-size: 12px; }
.upload-fields { display: flex; flex-direction: column; gap: 18px; }
.upload-fields label { display: flex; flex-direction: column; gap: 8px; }
.upload-actions { display: flex; justify-content: flex-end; gap: 8px; }
@media (max-width: 600px) { .keys-page { padding: 16px; } .key-status { grid-template-columns: 86px minmax(0, 1fr); } }
</style>
