<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';

const KEYS = {
  SSS: 'SSS评分开关',
  LOCAL_GM: '本地GM开关',
  NAME2: 'name2开关',
  ZH_PVF: '简体PVF'
} as const;

type ClientConfigBase = {
  '服务器地址': string;
  '角色等级上限': number;
  '一键卖分品级': number;
  '含宠物装备': number; // 0/1
  '史诗自动确认开关': number;
  '英雄级开关': number;
  '物品图标开关': number;
  '品级文本开关': number;
  '连发按键组': string[];
  '快捷键前置': 'Ctrl' | 'Shift' | 'Alt';
  '无损画质': number;
  '难度命名': string[]; // 固定 5
  '品级命名': string[]; // 固定 8
  '隐藏功能': number;

  '自动拾取': {
    '拾取模式': number; // 0~4
    '自定义拾取代码组': number[];
  };

  '自动翻牌': {
    '上': number; // 0~4
    '下': number; // 0~4
  };

  '史诗闪光': {
    '闪光开关': number;
    '闪光代码': number;
  };

  '补丁信息': {
    '补丁名称': string;
    '补丁声明': string;
  };
};

type ClientConfig = ClientConfigBase & {
  // 混合 ASCII/中文 key：用 KEYS 常量索引，避免类型里直接写出这些名称导致 IDE 警告
  [k: string]: any;
};

const loading = ref(false);

const defaultConfig = (): ClientConfig => ({
  '服务器地址': '49.232.12.79',
  '角色等级上限': 70,
  '一键卖分品级': 2,
  '含宠物装备': 0,
  [KEYS.SSS]: 1,
  [KEYS.LOCAL_GM]: 0,
  '史诗自动确认开关': 0,
  '英雄级开关': 1,
  '物品图标开关': 1,
  [KEYS.NAME2]: 1,
  '品级文本开关': 1,
  '连发按键组': ['X'],
  '快捷键前置': 'Ctrl',
  '无损画质': 16,
  '难度命名': ['普通级', '冒险级', '王者级', '地狱级', '英雄级'],
  '品级命名': ['普通', '高级', '稀有', '神器', '史诗', '勇者', '传说', '神话'],
  '隐藏功能': 0,
  '自动拾取': {
    '拾取模式': 4,
    '自定义拾取代码组': [0, 6515]
  },
  '自动翻牌': {
    '上': 0,
    '下': 0
  },
  '史诗闪光': {
    '闪光开关': 1,
    '闪光代码': 9413
  },
  '补丁信息': {
    '补丁名称': 'DOF补丁大合集V7',
    '补丁声明': '本软件永久免费！用途仅限于测试实验、研究学习为目的，请勿用于商业途径及非法运营，严禁将本软件用于与中国现行法律相违背的一切行为！否则，请停止使用，若坚持使用，造成的一切法律责任及所有后果均由使用方承担，与作者无关，特此声明！'
  }
});

const form = reactive<ClientConfig>(defaultConfig());

const HOTKEY_PREFIX_OPTIONS = [
  { label: 'Ctrl', value: 'Ctrl' },
  { label: 'Shift', value: 'Shift' },
  { label: 'Alt', value: 'Alt' }
];

const PICK_MODE_OPTIONS = [
  { label: '0 - 关闭', value: 0 },
  { label: '1 - 模式1', value: 1 },
  { label: '2 - 模式2', value: 2 },
  { label: '3 - 模式3', value: 3 },
  { label: '4 - 模式4', value: 4 }
];

const TURN_CARD_OPTIONS = [
  { label: '0 - 关闭', value: 0 },
  { label: '1 - 第1张', value: 1 },
  { label: '2 - 第2张', value: 2 },
  { label: '3 - 第3张', value: 3 },
  { label: '4 - 第4张', value: 4 }
];

const ensureFixedArray = (arr: any, len: number, defaultFactory: (i: number) => any) => {
  const next = Array.isArray(arr) ? [...arr] : [];
  while (next.length < len) next.push(defaultFactory(next.length));
  return next.slice(0, len);
};

const load = async () => {
  loading.value = true;
  try {
    const res = await Request.get<any>('/api/v1/admin/assist/config');

    // 兼容两种返回：
    // 1) 直接返回配置 JSON
    // 2) Result 包装：{ code,msg/data }
    const payload = res?.data;
    const cfg = payload && typeof payload === 'object' && payload.data ? payload.data : payload;

    const merged = { ...defaultConfig(), ...(cfg || {}) } as ClientConfig;

    // 修正关键字段类型与固定数组长度
    merged['难度命名'] = ensureFixedArray(merged['难度命名'], 5, (i) => defaultConfig()['难度命名'][i]);
    merged['品级命名'] = ensureFixedArray(merged['品级命名'], 8, (i) => defaultConfig()['品级命名'][i]);
    merged['连发按键组'] = Array.isArray(merged['连发按键组']) ? merged['连发按键组'] : ['X'];

    merged['自动拾取'] = { ...defaultConfig()['自动拾取'], ...(merged['自动拾取'] || {}) };
    merged['自动拾取']['自定义拾取代码组'] = Array.isArray(merged['自动拾取']['自定义拾取代码组'])
      ? merged['自动拾取']['自定义拾取代码组'].map((n: any) => Number(n)).filter((n: number) => !Number.isNaN(n))
      : [];

    merged['自动翻牌'] = { ...defaultConfig()['自动翻牌'], ...(merged['自动翻牌'] || {}) };
    merged['史诗闪光'] = { ...defaultConfig()['史诗闪光'], ...(merged['史诗闪光'] || {}) };
    merged['补丁信息'] = { ...defaultConfig()['补丁信息'], ...(merged['补丁信息'] || {}) };

    Object.assign(form, merged);
  } finally {
    loading.value = false;
  }
};

const save = async () => {
  loading.value = true;
  try {
    // 保存前做一次轻量归一化
    form['一键卖分品级'] = Math.min(6, Math.max(0, Number(form['一键卖分品级'] || 0)));
    form['角色等级上限'] = Math.min(150, Math.max(45, Number(form['角色等级上限'] || 45)));
    form['无损画质'] = Math.min(16, Math.max(0, Number(form['无损画质'] || 0)));

    form['难度命名'] = ensureFixedArray(form['难度命名'], 5, (i) => `难度${i + 1}`);
    form['品级命名'] = ensureFixedArray(form['品级命名'], 8, (i) => `品级${i + 1}`);

    form['连发按键组'] = Array.isArray(form['连发按键组'])
      ? form['连发按键组'].map((s) => String(s).trim()).filter(Boolean)
      : [];

    form['自动拾取']['拾取模式'] = Math.min(4, Math.max(0, Number(form['自动拾取']['拾取模式'] || 0)));
    form['自动翻牌']['上'] = Math.min(4, Math.max(0, Number(form['自动翻牌']['上'] || 0)));
    form['自动翻牌']['下'] = Math.min(4, Math.max(0, Number(form['自动翻牌']['下'] || 0)));

    await Request.put('/api/v1/admin/assist/config', JSON.parse(JSON.stringify(form)));
    Message.success('保存成功');
    await load();
  } finally {
    loading.value = false;
  }
};

onMounted(load);

// 组件：自定义拾取代码组（用 tags 输入，自动转 number）
const pickCodeTags = computed<string[]>({
  get: () => (Array.isArray(form['自动拾取']['自定义拾取代码组']) ? form['自动拾取']['自定义拾取代码组'].map(String) : []),
  set: (arr) => {
    form['自动拾取']['自定义拾取代码组'] = (arr || [])
      .map((s) => Number(String(s).trim()))
      .filter((n) => !Number.isNaN(n));
  }
});
</script>

<template>
  <a-spin :loading="loading" style="width: 100%">
    <a-space direction="vertical" size="large" fill>
      <a-card title="客户端配置" style="margin: 24px;">
        <template #extra>
          这些配置项会影响客户端程序的运行行为，请谨慎修改
        </template>

        <a-form layout="vertical" :model="form">
          <a-row :gutter="16">
            <a-col :span="6">
              <a-form-item label="服务器地址">
                <a-input v-model="form['服务器地址']" placeholder="例如 49.232.12.79" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="角色等级上限 (45~150)">
                <a-input-number v-model="form['角色等级上限']" :min="45" :max="150" style="width: 100%" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="一键卖分品级 (0~6)">
                <a-input-number v-model="form['一键卖分品级']" :min="0" :max="6" style="width: 100%" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="无损画质 (0~16)">
                <a-slider v-model="form['无损画质']" :min="0" :max="16" :step="1" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="含宠物装备">
                <a-switch v-model="form['含宠物装备']" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="SSS评分开关">
                <a-switch v-model="form[KEYS.SSS]" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="本地GM开关">
                <a-switch v-model="form[KEYS.LOCAL_GM]" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="史诗自动确认开关">
                <a-switch v-model="form['史诗自动确认开关']" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="英雄级开关">
                <a-switch v-model="form['英雄级开关']" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="物品图标开关">
                <a-switch v-model="form['物品图标开关']" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="name2开关">
                <a-switch v-model="form[KEYS.NAME2]" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="品级文本开关">
                <a-switch v-model="form['品级文本开关']" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="简体PVF">
                <a-switch v-model="form[KEYS.ZH_PVF]" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>

            <a-col :span="6">
              <a-form-item label="隐藏功能">
                <a-switch v-model="form['隐藏功能']" :checked-value="1" :unchecked-value="0" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
            </a-col>

            <a-col :span="12">
              <a-form-item label="快捷键前置">
                <a-select v-model="form['快捷键前置']" :options="HOTKEY_PREFIX_OPTIONS" />
              </a-form-item>
            </a-col>

            <a-col :span="12">
              <a-form-item label="连发按键组">
                <a-input-tag v-model="form['连发按键组']" placeholder="例如：X" allow-clear />
              </a-form-item>
            </a-col>
          </a-row>
        </a-form>
      </a-card>

      <a-card title="自动拾取" style="margin: 0 24px;">
        <template #extra>
          配置自动拾取物品的模式和自定义拾取代码组, 代码表示可自动拾取的物品ID
        </template>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="拾取模式">
              <a-select v-model="form['自动拾取']['拾取模式']" :options="PICK_MODE_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="自定义拾取代码组">
              <a-input-tag v-model="pickCodeTags" placeholder="输入后回车，例如：6515" allow-clear />
            </a-form-item>
          </a-col>
        </a-row>
      </a-card>
      <a-card title="命名配置" style="margin: 0 24px;">
        <template #extra>
          配置难度和品级的命名，方便在客户端显示自定义名称
        </template>
        <a-form-item label="难度命名">
          <a-space wrap>
            <a-input v-for="i in 5" :key="i" v-model="form['难度命名'][i-1]" :style="{ width: '160px' }" />
          </a-space>
        </a-form-item>
        <a-form-item label="品级命名">
          <a-space wrap>
            <a-input v-for="i in 8" :key="i" v-model="form['品级命名'][i-1]" :style="{ width: '160px' }" />
          </a-space>
        </a-form-item>
      </a-card>
      <a-card title="自动翻牌" style="margin: 0 24px;">
        <template #extra>
          配置通关后自动翻牌的选项, 0表示关闭翻牌功能, 1~4表示翻对应的牌
        </template>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="上">
              <a-select v-model="form['自动翻牌']['上']" :options="TURN_CARD_OPTIONS" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="下">
              <a-select v-model="form['自动翻牌']['下']" :options="TURN_CARD_OPTIONS" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-card>
      <a-card title="史诗闪光" style="margin: 0 24px;">
        <a-row :gutter="16">
          <a-col :span="4">
            <a-form-item label="闪光开关">
              <a-switch v-model="form['史诗闪光']['闪光开关']" :checked-value="1" :unchecked-value="0" />
            </a-form-item>
          </a-col>
          <a-col :span="20">
            <a-form-item label="闪光代码">
              <a-input-number v-model="form['史诗闪光']['闪光代码']" :min="0" style="width: 100%" />
            </a-form-item>
          </a-col>
        </a-row>
      </a-card>
      <a-col :span="24">
        <a-card title="补丁信息" style="margin: 0 24px;">
          <a-form-item label="补丁名称">
            <a-input v-model="form['补丁信息']['补丁名称']" />
          </a-form-item>
          <a-form-item label="补丁声明">
            <a-textarea v-model="form['补丁信息']['补丁声明']" :auto-size="{ minRows: 6, maxRows: 6 }" />
          </a-form-item>
        </a-card>
      </a-col>

      <div style="text-align: center">
        <a-space style="margin-bottom: 24px;">
          <a-button type="primary" @click="save">保存</a-button>
          <a-button @click="load">重新加载</a-button>
        </a-space>
      </div>
    </a-space>
  </a-spin>
</template>
