<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, nextTick } from 'vue';
import Request from '../../../api/Request';
import { Message } from '@arco-design/web-vue';
import ItemImg from '../../../components/ItemImg.vue';

type ItemIcon = {
  path: string;
  index: number;
};

type PvfItem = {
  id: number;
  rarity?: number;
  name?: string;
  type?: string; // equipment/stackable
  minimumLevel?: number;
  stackLimit?: number;
  attachType?: string;
  usableJobsStr?: string[];
  description?: string;
  explain?: string;
  // equipment / stackable extra fields (backend may return more)
  equipmentType?: string;
  equipmentTypeStr?: string;
  stackableType?: string;
  stackableTypeStr?: string;
  icon?: ItemIcon | null;
  [key: string]: any;
};

type PageResult<T> = {
  totalSize: number;
  page: number;
  pageSize: number;
  list: T[];
};

type PvfInfo = {
  size: string;
  equipmentCount: number;
  itemCount: number;
  canUpload: boolean;
};

const pvfInfo = ref<PvfInfo>({
  size: '0 KB',
  equipmentCount: 0,
  itemCount: 0,
  canUpload: false
});

const loading = ref(false);
const list = ref<PvfItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(30);

const keyword = ref('');

type Category = { key: string; label: string; type: 'equipment' | 'stackable' | ''; subType: string };

const categoryGroups = ref([
  {
    key: 'equipment',
    label: '装备',
    children: [
      { key: 'equipment_all', label: '全部', type: 'equipment', subType: '' },
      { key: 'weapon', label: '武器', type: 'equipment', subType: 'weapon' },
      { key: 'titleName', label: '称号', type: 'equipment', subType: 'titleName' },
      { key: 'coat', label: '上衣', type: 'equipment', subType: 'coat' },
      { key: 'shoulder', label: '护肩', type: 'equipment', subType: 'shoulder' },
      { key: 'pants', label: '裤子', type: 'equipment', subType: 'pants' },
      { key: 'shoes', label: '鞋子', type: 'equipment', subType: 'shoes' },
      { key: 'waist', label: '腰带', type: 'equipment', subType: 'waist' },
      { key: 'amulet', label: '项链', type: 'equipment', subType: 'amulet' },
      { key: 'wrist', label: '手镯', type: 'equipment', subType: 'wrist' },
      { key: 'ring', label: '戒指', type: 'equipment', subType: 'ring' },
      { key: 'support', label: '辅助装备', type: 'equipment', subType: 'support' },
      { key: 'magicStone', label: '魔法石', type: 'equipment', subType: 'magicStone' },
      { key: 'creature', label: '宠物', type: 'equipment', subType: 'creature' }
    ] as Category[]
  },
  {
    key: 'stackable',
    label: '道具',
    children: [
      { key: 'stackable_all', label: '全部', type: 'stackable', subType: '' },
      { key: 'waste', label: '消耗品', type: 'stackable', subType: 'waste' },
      { key: 'material', label: '材料', type: 'stackable', subType: 'material' },
      { key: 'recipe', label: '设计图', type: 'stackable', subType: 'recipe' },
      { key: 'material_expert_job', label: '副职业', type: 'stackable', subType: 'material_expert_job' },
      { key: 'quest', label: '任务道具', type: 'stackable', subType: 'quest' },
      { key: 'booster', label: '礼盒', type: 'stackable', subType: 'booster' },
      { key: 'feed', label: '饲料', type: 'stackable', subType: 'feed' },
      { key: 'creature', label: '宠物', type: 'stackable', subType: 'creature' },
      { key: 'etc', label: '杂物', type: 'stackable', subType: 'etc' },
      { key: 'throwItem', label: '投掷物', type: 'stackable', subType: 'throwItem' },
      { key: 'legacy', label: '罐子', type: 'stackable', subType: 'legacy' }
    ] as Category[]
  }
]);

const allCategory: Category = { key: 'all', label: '全部分类', type: '', subType: '' };
const selectedCategoryKey = ref(allCategory.key);
const selectedCategory = computed<Category>(() => {
  if (selectedCategoryKey.value === allCategory.key) return allCategory;
  for (const group of categoryGroups.value) {
    const found = group.children.find((c: Category) => c.key === selectedCategoryKey.value);
    if (found) return found;
  }
  return allCategory;
});

const groupOpen = ref<Record<string, boolean>>({ equipment: true, stackable: true });

const rarityColor = (rarity?: number) => {
  switch (Number(rarity ?? 0)) {
    case 1:
      return '#15a9e8';
    case 2:
      return '#7111ff';
    case 3:
      return '#db00fd';
    case 4:
      return '#fdb500';
    case 5:
      return '#fd0000';
    case 6:
      return 'transparent';
    default:
      return '#e5e7eb';
  }
};

const rarityGradient = (rarity?: number) => {
  return Number(rarity ?? 0) === 6
    ? 'linear-gradient(45deg, #db00fd, #f474fb, #7300ff, #5ef2f6)'
    : '';
};

const getTypeText = (it: PvfItem) => {
  return it.equipmentTypeStr || it.stackableTypeStr || it.equipmentType || it.stackableType || it.type || '';
};

const formatAttack = (val: any): string => {
  if (val == null) return '';
  if (Array.isArray(val)) return String(val[0] ?? '');
  return String(val);
};

const formatSpeed = (val: any): string => {
  if (val == null) return '';
  return (Number(val) / 10).toFixed(1).replace(/\.0$/, '') + '%';
};

const hoverAttackRows = computed(() => {
  const it = hoverItem.value;
  if (!it) return [];
  const rows: Array<{ label: string; value: string }> = [];
  // 攻击力
  if (it.physicalAttack != null) rows.push({ label: '物理攻击力', value: formatAttack(it.physicalAttack) });
  if (it.magicalAttack != null) rows.push({ label: '魔法攻击力', value: formatAttack(it.magicalAttack) });
  if (it.separateAttack != null) rows.push({ label: '独立攻击力', value: formatAttack(it.separateAttack) });
  // 防御力
  if (it.physicalDefense != null) rows.push({ label: '物理防御力', value: formatAttack(it.physicalDefense) });
  if (it.magicalDefense != null) rows.push({ label: '魔法防御力', value: formatAttack(it.magicalDefense) });
  // 四维
  if (it.strength != null) rows.push({ label: '力量', value: String(it.strength) });
  if (it.intelligence != null) rows.push({ label: '智力', value: String(it.intelligence) });
  if (it.vitality != null) rows.push({ label: '体力', value: String(it.vitality) });
  if (it.spirit != null) rows.push({ label: '精神', value: String(it.spirit) });
  // HP/MP
  if (it.hpMax != null) rows.push({ label: 'HP最大值', value: String(it.hpMax) });
  if (it.mpMax != null) rows.push({ label: 'MP最大值', value: String(it.mpMax) });
  // 抗魔值
  if (it.antiEvil != null) rows.push({ label: '抗魔值', value: String(it.antiEvil) });
  return rows;
});

const hoverBonusRows = computed(() => {
  const it = hoverItem.value;
  if (!it) return [];
  const rows: Array<{ label: string; value: string }> = [];
  // 速度
  if (it.attackSpeed != null) rows.push({ label: '攻击速度', value: formatSpeed(it.attackSpeed) });
  if (it.castSpeed != null) rows.push({ label: '施放速度', value: formatSpeed(it.castSpeed) });
  if (it.moveSpeed != null) rows.push({ label: '移动速度', value: formatSpeed(it.moveSpeed) });
  // 暴击
  if (it.physicalCriticalHit != null) rows.push({ label: '物理暴击率', value: String(it.physicalCriticalHit) + '%' });
  if (it.magicalCriticalHit != null) rows.push({ label: '魔法暴击率', value: String(it.magicalCriticalHit) + '%' });
  // 命中/回避/硬直/跳跃
  if (it.hitRate != null) rows.push({ label: '命中率', value: String(it.hitRate) });
  if (it.dodge != null) rows.push({ label: '回避率', value: String(it.dodge) });
  if (it.hitRecovery != null) rows.push({ label: '硬直', value: String(it.hitRecovery) });
  if (it.jumpPower != null) rows.push({ label: '跳跃力', value: String(it.jumpPower) });
  // 属性攻击
  if (it.fireElement != null) rows.push({ label: '火属性攻击', value: '' });
  if (it.waterElement != null) rows.push({ label: '冰属性攻击', value: '' });
  if (it.lightElement != null) rows.push({ label: '光属性攻击', value: '' });
  if (it.darkElement != null) rows.push({ label: '暗属性攻击', value: '' });
  // 属性强化
  if (it.fireAttack != null) rows.push({ label: '火属性强化', value: String(it.fireAttack) });
  if (it.waterAttack != null) rows.push({ label: '冰属性强化', value: String(it.waterAttack) });
  if (it.lightAttack != null) rows.push({ label: '光属性强化', value: String(it.lightAttack) });
  if (it.darkAttack != null) rows.push({ label: '暗属性强化', value: String(it.darkAttack) });
  if (it.allElementalAttack != null) rows.push({ label: '全属性强化', value: String(it.allElementalAttack) });
  // 属性抗性
  if (it.fireResistance != null) rows.push({ label: '火属性抗性', value: String(it.fireResistance) });
  if (it.waterResistance != null) rows.push({ label: '冰属性抗性', value: String(it.waterResistance) });
  if (it.lightResistance != null) rows.push({ label: '光属性抗性', value: String(it.lightResistance) });
  if (it.darkResistance != null) rows.push({ label: '暗属性抗性', value: String(it.darkResistance) });
  if (it.allElementalResistance != null) rows.push({ label: '所有属性抗性', value: String(it.allElementalResistance) });
  // 异常状态抗性
  if (it.blindResistance != null) rows.push({ label: '失明抗性', value: String(it.blindResistance) });
  if (it.lightningResistance != null) rows.push({ label: '感电抗性', value: String(it.lightningResistance) });
  if (it.burnResistance != null) rows.push({ label: '灼伤抗性', value: String(it.burnResistance) });
  if (it.freezeResistance != null) rows.push({ label: '冰冻抗性', value: String(it.freezeResistance) });
  if (it.holdResistance != null) rows.push({ label: '束缚抗性', value: String(it.holdResistance) });
  if (it.sleepResistance != null) rows.push({ label: '睡眠抗性', value: String(it.sleepResistance) });
  if (it.bleedingResistance != null) rows.push({ label: '出血抗性', value: String(it.bleedingResistance) });
  if (it.confuseResistance != null) rows.push({ label: '混乱抗性', value: String(it.confuseResistance) });
  if (it.curseResistance != null) rows.push({ label: '诅咒抗性', value: String(it.curseResistance) });
  if (it.stoneResistance != null) rows.push({ label: '石化抗性', value: String(it.stoneResistance) });
  if (it.allActiveStatusResistance != null) rows.push({ label: '所有异常状态抗性', value: String(it.allActiveStatusResistance) });
  // 其他
  if (it.inventoryLimit != null) rows.push({ label: '负重', value: String(it.inventoryLimit) });
  if (it.hpRegenSpeed != null) rows.push({ label: 'HP恢复速度', value: String(it.hpRegenSpeed) });
  if (it.mpRegenSpeed != null) rows.push({ label: 'MP恢复速度', value: String(it.mpRegenSpeed) });
  if (it.roomListMoveSpeedRate != null) rows.push({ label: '城镇移动速度', value: String(it.roomListMoveSpeedRate) });
  return rows;
});

const getJobText = (it: PvfItem) => {
  if (Array.isArray(it.usableJobsStr) && it.usableJobsStr.length) return it.usableJobsStr.join(' / ');
  return '';
};

const buildSearchUrl = () => {
  const params: string[] = [];
  const kw = keyword.value.trim();
  const cat = selectedCategory.value;
  if (kw) params.push(`keyword=${encodeURIComponent(kw)}`);
  if (cat.type) params.push(`type=${encodeURIComponent(cat.type)}`);
  if (cat.subType) params.push(`subType=${encodeURIComponent(cat.subType)}`);
  params.push(`page=${page.value}`);
  params.push(`pageSize=${pageSize.value}`);
  return `/api/v1/pvf/search${params.length ? `?${params.join('&')}` : ''}`;
};

const loadPvfInfo = async () => {
  const res = await Request.get('/api/v1/pvf/info');
  pvfInfo.value = {
    size: res.data?.size ?? '0 KB',
    equipmentCount: res.data?.equipmentCount ?? 0,
    itemCount: res.data?.itemCount ?? 0,
    canUpload: !!res.data?.canUpload
  };
};

const loadList = async () => {
  loading.value = true;
  try {
    const res = await Request.get<PageResult<PvfItem>>(buildSearchUrl());
    list.value = res.data?.list ?? [];
    total.value = res.data?.totalSize ?? 0;
  } catch (e: any) {
    Message.error(e?.message || '加载失败');
  } finally {
    loading.value = false;
  }
};

let kwTimer: number | null = null;
const onKeywordInput = () => {
  page.value = 1;
  if (kwTimer) window.clearTimeout(kwTimer);
  kwTimer = window.setTimeout(() => {
    loadList();
  }, 300);
};

const onSelectCategory = (key: string) => {
  selectedCategoryKey.value = key;
  page.value = 1;
  loadList();
};

const onPageChange = (p: number) => {
  page.value = p;
  loadList();
};

// hover detail panel
const hoverItem = ref<PvfItem | null>(null);
const hoverPos = ref({ x: 0, y: 0 });
const updateHoverPos = (evt: MouseEvent) => {
  hoverPos.value = { x: evt.clientX + 16, y: evt.clientY + 16 };
};

const rowsForHover = computed(() => {
  if (!hoverItem.value) return [] as Array<{ k: string; v: string }>;
  const it = hoverItem.value;

  // Priority fields first (roughly equals the old table columns)
  const known: Array<[string, any]> = [
    ['ID', it.id],
    ['名称', it.name],
    ['类型', getTypeText(it)],
    ['等级', it.minimumLevel],
    ['稀有度', it.rarity],
    ['交易类型', it.attachType],
    ['携带上限', it.stackLimit],
    ['职业', getJobText(it)],
    ['说明(面板)', it.explain],
    ['描述', it.description]
  ];

  const out: Array<{ k: string; v: string }> = [];
  const usedKeys = new Set<string>();

  for (const [k, v] of known) {
    if (v == null || v === '' || (Array.isArray(v) && v.length === 0)) continue;
    out.push({ k, v: Array.isArray(v) ? v.join(', ') : String(v) });
  }

  // Append any extra fields returned by backend
  for (const key of Object.keys(it)) {
    if (
      ['icon'].includes(key) ||
      typeof it[key] === 'function'
    ) {
      continue;
    }
    // skip duplicates already shown
    if (
      [
        'id',
        'name',
        'type',
        'equipmentType',
        'equipmentTypeStr',
        'stackableType',
        'stackableTypeStr',
        'minimumLevel',
        'rarity',
        'attachType',
        'stackLimit',
        'usableJobsStr',
        'description',
        'explain'
      ].includes(key)
    ) {
      continue;
    }

    if (usedKeys.has(key)) continue;
    const v = it[key];
    if (v == null || v === '') continue;

    if (typeof v === 'object') {
      try {
        out.push({ k: key, v: JSON.stringify(v) });
      } catch {
        out.push({ k: key, v: String(v) });
      }
    } else {
      out.push({ k: key, v: String(v) });
    }
  }

  return out;
});

// upload
const fileInput = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const selectPvfFile = () => {
  if (!pvfInfo.value.canUpload) return;
  fileInput.value?.click();
};

const handleFileUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  uploading.value = true;
  try {
    const data = new FormData();
    data.append('file', file);
    await Request.post('/api/v1/pvf/upload', data, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    Message.success('上传成功，PVF已更新');
    await loadPvfInfo();
    await loadList();
  } finally {
    uploading.value = false;
    // reset input so selecting the same file again triggers change
    target.value = '';
  }
};

const rootEl = ref<HTMLElement | null>(null);
const headerEl = ref<HTMLElement | null>(null);
const alertEl = ref<HTMLElement | null>(null);
const toolbarEl = ref<HTMLElement | null>(null);
const paginationEl = ref<HTMLElement | null>(null);
const bodyEl = ref<HTMLElement | null>(null);
const leftTitleEl = ref<HTMLElement | null>(null);

const leftScrollH = ref<number>(360);
const rightScrollH = ref<number>(360);

const measureHeights = () => {
  // 以 pvf-body 的实际高度为准（它就是“剩余空间”）
  const body = bodyEl.value;
  if (!body) return;

  const bodyH = Math.max(200, Math.floor(body.getBoundingClientRect().height));

  const leftTitleH = leftTitleEl.value?.getBoundingClientRect().height ?? 0;
  leftScrollH.value = Math.max(160, Math.floor(bodyH - leftTitleH));

  const toolbarH = toolbarEl.value?.getBoundingClientRect().height ?? 0;
  const paginationH = paginationEl.value?.getBoundingClientRect().height ?? 0;

  // right 本身有 padding(12*2)；scrollbar/pagination/toolbar 都在 right 内，
  // bodyH 已经包含 right 的 padding，因此要减掉它以得到内容可用高度。
  const rightPadding = 24;
  const rightContentH = Math.max(160, bodyH - rightPadding);
  rightScrollH.value = Math.max(160, Math.floor(rightContentH - toolbarH - paginationH));
};

let rafId: number | null = null;
const scheduleMeasure = () => {
  if (rafId != null) cancelAnimationFrame(rafId);
  rafId = requestAnimationFrame(() => {
    rafId = null;
    measureHeights();
  });
};

let ro: ResizeObserver | null = null;

onMounted(async () => {
  await nextTick();
  measureHeights();

  // 监听高度变化（窗口 resize、tab 切换、字体加载、alert 折行等）
  ro = new ResizeObserver(() => scheduleMeasure());
  if (rootEl.value) ro.observe(rootEl.value);
  if (bodyEl.value) ro.observe(bodyEl.value);
  if (headerEl.value) ro.observe(headerEl.value);
  if (alertEl.value) ro.observe(alertEl.value);
  if (toolbarEl.value) ro.observe(toolbarEl.value);
  if (paginationEl.value) ro.observe(paginationEl.value);

  window.addEventListener('resize', scheduleMeasure);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', scheduleMeasure);
  if (rafId != null) cancelAnimationFrame(rafId);
  if (ro) {
    ro.disconnect();
    ro = null;
  }
});

onMounted(async () => {
  try {
    await loadPvfInfo();
  } catch (e: any) {
    Message.error(e?.message || 'PVF信息加载失败');
  }
  await loadList();
});
</script>

<template>
  <div ref="rootEl" class="pvf-page" @mousemove="updateHoverPos">
    <div ref="headerEl" class="pvf-header">
      <div class="pvf-title">PVF 管理</div>
      <div class="pvf-sub">
        当前PVF：{{ pvfInfo.size }} · 装备：{{ pvfInfo.equipmentCount }} · 道具：{{ pvfInfo.itemCount }}
      </div>
      <div class="pvf-actions">
        <a-button type="primary" :disabled="!pvfInfo.canUpload || uploading" @click="selectPvfFile">
          上传新PVF
        </a-button>
        <input ref="fileInput" type="file" style="display: none" @change="handleFileUpload" />
      </div>
    </div>

    <a-alert
      ref="alertEl"
      type="warning"
      :show-icon="true"
      class="pvf-alert"
    >
      <template #title>注意</template>
      上传新的 PVF 会影响全局材料、任务等信息，可能导致系统异常，仅最高管理可操作。
    </a-alert>

    <div ref="bodyEl" class="pvf-body">
      <div class="left">
        <div ref="leftTitleEl" class="left-title">分类</div>
        <a-scrollbar
          class="left-scroll"
          :style="{ height: (leftScrollH - 26) + 'px' , overflow: 'auto' }"
        >
          <div class="menu-content">
            <div class="category-item" :class="{ active: selectedCategoryKey === allCategory.key }" @click="onSelectCategory(allCategory.key)">
              全部分类
            </div>

            <div class="category-group" v-for="g in categoryGroups" :key="g.key">
              <div class="category-group-header" @click="groupOpen[g.key] = !groupOpen[g.key]">
                <span>{{ g.label }}</span>
                <span class="chevron">{{ groupOpen[g.key] ? '▾' : '▸' }}</span>
              </div>

              <div v-show="groupOpen[g.key]" class="category-group-body">
                <div
                  v-for="c in g.children"
                  :key="c.key"
                  class="category-item"
                  :class="{ active: selectedCategoryKey === c.key }"
                  @click="onSelectCategory(c.key)"
                >
                  {{ c.label }}
                </div>
              </div>
            </div>
          </div>
        </a-scrollbar>
      </div>

      <div class="right">
        <div ref="toolbarEl" class="toolbar">
          <a-input
            v-model="keyword"
            allow-clear
            placeholder="搜索名称或ID"
            style="width: 320px"
            @input="onKeywordInput"
          />
          <div class="count">共 {{ total }} 条</div>
        </div>

        <a-scrollbar
          class="list-scroll"
          :style="{ height: (rightScrollH - 24) + 'px', overflow: 'auto' }"
        >
          <div v-if="loading" class="state">加载中...</div>
          <div v-else-if="list.length === 0" class="state">暂无数据</div>

          <div v-else class="item-list">
            <div
              v-for="it in list"
              :key="it.id"
              class="item-row"
              @mouseenter="hoverItem = it"
              @mouseleave="hoverItem = null"
            >
              <div class="item-icon">
                <ItemImg :icon="(it.icon || { path: '', index: 0 })" :rarity="it.rarity ?? 0" />
              </div>

              <div class="item-main">
                <div
                  class="item-name"
                  :style="
                    it.rarity === 6
                      ? { backgroundImage: rarityGradient(it.rarity), WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }
                      : { color: rarityColor(it.rarity) }
                  "
                >
                  {{ it.name || '未命名' }}
                </div>

                <div class="item-sub">
                  <span>Lv. {{ it.minimumLevel ?? 1 }}</span>
                  <span class="dot">·</span>
                  <span>{{ getTypeText(it) || '未知类型' }}</span>
                  <template v-if="getJobText(it)">
                    <span class="dot">·</span>
                    <span class="job">{{ getJobText(it) }}</span>
                  </template>
                </div>
              </div>

              <div class="item-meta">
                <div class="id">#{{ it.id }}</div>
                <div class="kind">{{ (it.type || '').toUpperCase() }}</div>
              </div>
            </div>
          </div>
        </a-scrollbar>

        <div ref="paginationEl" class="pagination">
          <a-pagination
            :current="page"
            :page-size="pageSize"
            :total="total"
            :show-jumper="true"
            @change="onPageChange"
          />
        </div>
      </div>

      <div v-if="hoverItem" class="hover-panel" :style="{ left: hoverPos.x + 'px', top: hoverPos.y + 'px' }">
        <div class="hover-left">
          <ItemImg :icon="(hoverItem.icon || { path: '', index: 0 })" :rarity="hoverItem.rarity ?? 0" style="width: 48px; height: 48px" />
        </div>
        <div class="hover-right">
          <div class="hover-title-row">
            <span
              class="hover-title"
              :style="
                hoverItem.rarity === 6
                  ? { backgroundImage: rarityGradient(hoverItem.rarity), WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }
                  : { color: rarityColor(hoverItem.rarity) }
              "
            >{{ hoverItem.name || '未命名' }}</span>
            <span class="hover-item-id">#{{ hoverItem.id }}</span>
          </div>
          <div class="hover-rarity" :style="{ color: rarityColor(hoverItem.rarity) }">
            {{ hoverItem.rarityName || '普通' }}
          </div>
          <div class="hover-info">
            <template v-if="hoverItem.weight"><span>{{ (hoverItem.weight / 1000).toFixed(1).replace(/\.0$/, '') }}kg</span></template>
            <template v-if="hoverItem.price"><span class="hover-price">{{ hoverItem.price }}金币</span></template>
          </div>
          <div class="hover-info">
            <template v-if="hoverItem.equipmentTypeStr"><span>{{ hoverItem.equipmentTypeStr }}</span></template>
            <template v-if="hoverItem.itemGroupStr"><span>{{ hoverItem.itemGroupStr }}</span></template>
          </div>
          <div class="hover-info" v-if="getJobText(hoverItem)">
            <span class="hover-jobs">可用职业：{{ getJobText(hoverItem) }}</span>
          </div>
          <div class="hover-info">
            <template v-if="hoverItem.durability"><span>耐久度：{{ hoverItem.durability }}/{{ hoverItem.durability }}</span></template>
            <template v-if="hoverItem.stackLimit && hoverItem.stackLimit > 1"><span>堆叠上限：{{ hoverItem.stackLimit }}</span></template>
            <template v-if="hoverItem.attachTypeStr"><span class="hover-attach" :class="{ 'attach-trade': hoverItem.attachTypeStr === '不可交易' }">{{ hoverItem.attachTypeStr }}</span></template>
          </div>
          <div class="hover-info">
            <template v-if="hoverItem.minimumLevel"><span>Lv{{ hoverItem.minimumLevel }}级以上可以使用</span></template>
          </div>
          <div class="hover-stats" v-if="hoverAttackRows.length">
            <div class="hover-stat-row" v-for="row in hoverAttackRows" :key="row.label">
              <span class="hover-stat-label">{{ row.label }}</span>
              <span class="hover-stat-value">+{{ row.value }}</span>
            </div>
          </div>
          <div class="hover-bonus" v-if="hoverBonusRows.length">
            <div class="hover-bonus-row" v-for="row in hoverBonusRows" :key="row.label">
              {{ row.label }}{{ row.value ? ' +' + row.value : '' }}
            </div>
          </div>
          <div class="hover-explain" v-if="hoverItem.explain">{{ hoverItem.explain.replaceAll('%%', '%').replaceAll('\\n', '\n') }}</div>
          <div class="hover-desc" v-if="hoverItem.description">{{ hoverItem.description.replaceAll('%%', '%').replaceAll('\\n', '\n') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="less">
.pvf-page {
  padding: 12px;
  height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.pvf-header {
  flex: none;
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 10px;
}

.pvf-alert {
  flex: none;
  margin-bottom: 12px;
}

.pvf-body {
  flex: 1;
  display: flex;
  gap: 12px;
  min-height: 0;
  overflow: hidden;
}

.left {
  flex: none;
  width: 220px;
  background: #111827;
  border: 1px solid #1f2937;
  border-radius: 10px;
  padding: 12px;
  box-sizing: border-box;
  color: #e5e7eb;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.left-scroll {
  flex: 1;
  min-height: 0;

  /* 让 Arco Scrollbar 在 flex 容器里真正占满并接管滚动 */
  :deep(.arco-scrollbar) {
    height: 100%;
  }
  :deep(.arco-scrollbar-container) {
    height: 100%;
    overflow: auto; /* 关键：必须可滚动，Arco 才会渲染自己的滚动条 */
  }
  :deep(.arco-scrollbar-content) {
    min-height: 100%;
  }
}

.pvf-page :deep(.menu-content) {
  padding: 2px;
  border-radius: 8px;
}

.pvf-page :deep(.menu-content .category-item) {
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  color: #d1d5db;
  margin-top: 4px;
  user-select: none;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.pvf-page :deep(.menu-content .category-item:hover) {
  background: #1f2937;
}

.pvf-page :deep(.menu-content .category-item.active) {
  background: #334155;
  color: #fff;
}

.pvf-page :deep(.menu-content .category-group) {
  margin-top: 10px;
}

.pvf-page :deep(.menu-content .category-group-header) {
  display: flex;
  justify-content: space-between;
  font-weight: 600;
  padding: 6px 8px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  user-select: none;
}

.pvf-page :deep(.menu-content .category-group-body) {
  margin-top: 6px;
}

/* 子分类缩进（仅分组内的分类项） */
.pvf-page :deep(.menu-content .category-group-body .category-item) {
  padding-left: 18px;
}

.pvf-page :deep(.menu-content .chevron) {
  color: #9ca3af;
}

/* 右侧列表滚动区 */
.list-scroll {
  flex: 1;
  min-height: 0;

  :deep(.arco-scrollbar) {
    height: 100%;
  }
  :deep(.arco-scrollbar-container) {
    height: 100%;
    overflow: auto;
  }
  :deep(.arco-scrollbar-content) {
    min-height: 100%;
  }
}

.right {
  flex: 1;
  background: #0b1220;
  border: 1px solid #1f2937;
  border-radius: 10px;
  padding: 12px;
  box-sizing: border-box;
  min-width: 0;
  color: #e5e7eb;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.toolbar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.count {
  color: #94a3b8;
  font-size: 12px;
}

.state {
  padding: 24px;
  text-align: center;
  color: #94a3b8;
}

.item-list {
  padding-right: 6px;
}

.item-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: #111827;
  border: 1px solid transparent;
  margin-bottom: 10px;
  transition: all 0.15s ease;
}

.item-row:hover {
  border-color: #334155;
  background: #0f172a;
}

.item-icon {
  flex: none;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-name {
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-sub {
  font-size: 12px;
  color: #b5b8c5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-sub .dot {
  margin: 0 6px;
  color: #64748b;
}

.item-sub .job {
  color: #cbd5f5;
}

.item-meta {
  flex: none;
  min-width: 70px;
  text-align: right;
  color: #94a3b8;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pagination {
  flex: none;
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;

  :deep(.arco-pagination) {
    color: #cbd5e1;
  }

  /* 页码按钮 */
  :deep(.arco-pagination-item),
  :deep(.arco-pagination-prev),
  :deep(.arco-pagination-next),
  :deep(.arco-pagination-jumper-button) {
    background: rgba(255, 255, 255, 0.06);
    border: 1px solid rgba(148, 163, 184, 0.25);
    color: #e5e7eb;
  }

  :deep(.arco-pagination-item:hover),
  :deep(.arco-pagination-prev:hover),
  :deep(.arco-pagination-next:hover),
  :deep(.arco-pagination-jumper-button:hover) {
    background: rgba(255, 255, 255, 0.10);
    border-color: rgba(148, 163, 184, 0.45);
  }

  /* 当前页 */
  :deep(.arco-pagination-item-active) {
    background: rgba(56, 189, 248, 0.18);
    border-color: rgba(56, 189, 248, 0.55);
    color: #e5e7eb;
  }

  /* 禁用状态 */
  :deep(.arco-pagination-item-disabled),
  :deep(.arco-pagination-prev-disabled),
  :deep(.arco-pagination-next-disabled) {
    opacity: 0.45;
  }

  /* jumper 输入框 */
  :deep(.arco-pagination-jumper input),
  :deep(.arco-pagination-jumper .arco-input-wrapper),
  :deep(.arco-pagination-jumper .arco-input) {
    background: rgba(15, 23, 42, 0.6);
    border-color: rgba(148, 163, 184, 0.25);
    color: #e5e7eb;
  }

  :deep(.arco-pagination-jumper input::placeholder) {
    color: rgba(203, 213, 225, 0.55);
  }
}

.hover-panel {
  position: fixed;
  z-index: 9999;
  background: rgba(17, 24, 39, 0.96);
  color: #e5e7eb;
  padding: 12px;
  border-radius: 10px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.45);
  max-width: 420px;
  pointer-events: none;
  display: flex;
  gap: 12px;
}

.hover-left {
  flex: none;
}

.hover-right {
  flex: 1;
  min-width: 0;
}

.hover-title-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}

.hover-title {
  font-weight: 700;
  font-size: 14px;
}

.hover-item-id {
  color: #64748b;
  font-size: 11px;
}

.hover-rarity {
  font-size: 12px;
  margin-bottom: 4px;
}

.hover-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 2px;
}

.hover-price {
  color: #fbbf24;
}

.hover-jobs {
  color: #cbd5e1;
  text-align: right;
}

.hover-attach {
  color: #9ca3af;
}

.hover-attach.attach-trade {
  color: #ef4444;
}

.hover-stats {
  margin-top: 6px;
  margin-bottom: 2px;
}

.hover-stat-row {
  font-size: 12px;
  margin-bottom: 1px;
}

.hover-stat-label {
  color: #d1d5db;
}

.hover-stat-value {
  color: #e2e8f0;
}

.hover-bonus {
  margin-top: 4px;
  margin-bottom: 2px;
}

.hover-bonus-row {
  font-size: 12px;
  color: #60a5fa;
  margin-bottom: 1px;
}

.hover-explain {
  margin-top: 6px;
  font-size: 12px;
  color: #60a5fa;
  line-height: 1.5;
  white-space: pre-wrap;
}

.hover-desc {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7280;
  font-style: italic;
  line-height: 1.4;
  white-space: pre-wrap;
}
</style>
