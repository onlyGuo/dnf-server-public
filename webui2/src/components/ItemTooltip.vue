<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import ItemImg from './ItemImg.vue';
import { getJobText, rarityColor, rarityGradient, type PvfItem } from './itemDetails';

const props = defineProps<{ item: PvfItem; x: number; y: number }>();
const hoverItem = computed(() => props.item);

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

const tooltipEl = ref<HTMLElement | null>(null);
const tooltipStyle = ref({ left: `${props.x}px`, top: `${props.y}px` });
watch(() => [props.item, props.x, props.y], async () => {
  await nextTick();
  const box = tooltipEl.value?.getBoundingClientRect();
  if (!box) return;
  const margin = 8;
  const left = props.x + box.width <= window.innerWidth - margin
    ? props.x : props.x - box.width - 32;
  const top = props.y + box.height <= window.innerHeight - margin
    ? props.y : props.y - box.height - 32;
  tooltipStyle.value = {
    left: `${Math.max(margin, Math.min(left, window.innerWidth - box.width - margin))}px`,
    top: `${Math.max(margin, Math.min(top, window.innerHeight - box.height - margin))}px`
  };
}, { immediate: true, flush: 'post' });
</script>

<template>
  <Teleport to="body">
    <div v-if="hoverItem" ref="tooltipEl" class="hover-panel" role="tooltip" :style="tooltipStyle">
        <div class="hover-left">
          <ItemImg :icon="(hoverItem.icon || { path: '', index: 0 })" :icon-marks="hoverItem.iconMarks" :rarity="hoverItem.rarity ?? 0" style="width: 48px; height: 48px" />
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
  </Teleport>
</template>

<style scoped>
.hover-panel {
  position: fixed;
  z-index: 9999;
  background: rgba(17, 24, 39, 0.96);
  color: #e5e7eb;
  padding: 12px;
  border-radius: 10px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.45);
  box-sizing: border-box;
  max-width: min(420px, calc(100vw - 16px));
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
