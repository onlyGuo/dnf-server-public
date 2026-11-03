<template>
  <div class="dnf-icon-wrapper" :class="rarityClass" title="图标预览">
    <!-- 正常显示图片 -->
    <img
        v-if="shouldShowImage"
        :src="imageUrl"
        alt="icon"
        loading="lazy"
        @error="handleError"
    />

    <!-- 异常/无图时显示红叉 -->
    <img
        v-else
        :src="redCrossSrc"
        alt="无图"
        class="error-icon"
    />
    <template v-for="(mark, index) in markImages" :key="`${index}:${mark.url}`">
      <img
          v-if="!failedMarkUrls.has(mark.url)"
          :src="mark.url"
          alt=""
          aria-hidden="true"
          class="item-icon-mark"
          loading="lazy"
          @error="handleMarkError(mark.url)"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { imagePackRevision } from './imagePackRevision';

// 定义 Props
const props = defineProps({
  // 稀有度: 0=普通, 1=高级, 2=稀有, 3=神器, 4=史诗, 5=勇者, 6=神话
  rarity: {
    type: Number,
    default: 0,
    validator: (value) => value >= 0 && value <= 6
  },
  // 图标对象
  icon: {
    type: Object,
    required: true,
    default: () => ({ path: '', index: 0 })
  },
  iconMarks: {
    type: Array,
    default: () => []
  }
});

// 状态：是否加载失败
const isLoadError = ref(false);
const failedMarkUrls = ref(new Set());

// 监听 props 变化，如果路径变了，重置错误状态，尝试重新加载
watch(() => props.icon, () => {
  isLoadError.value = false;
}, { deep: true });
watch(() => props.iconMarks, () => {
  failedMarkUrls.value = new Set();
}, { deep: true });
watch(imagePackRevision, () => {
  isLoadError.value = false;
  failedMarkUrls.value = new Set();
});

// 基础红叉图片 (SVG Base64)，红色 X
const redCrossSrc = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAzMiAzMiI+PHBhdGggc3Ryb2tlPSIjZmYwMDAwIiBzdHJva2Utd2lkdGg9IjQiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIgZD0iTTYgNiBMMjYgMjYgTTI2IDYgTDYgMjYiIGZpbGw9Im5vbmUiLz48L3N2Zz4=";

// 计算图片地址
const imageUrl = computed(() => {
  if (!props.icon || !props.icon.path) return '';
  // 拼接路径
  return `/api/v1/pvf/img?path=${encodeURIComponent(`sprite/${props.icon.path}`)}&index=${encodeURIComponent(props.icon.index)}&v=${encodeURIComponent(imagePackRevision.value)}`;
});

const markImages = computed(() => {
  return props.iconMarks.filter(mark => mark?.path).map(mark => {
    const path = String(mark.path).replace(/\\/g, '/').replace(/^\/+/, '');
    const spritePath = path.toLowerCase().startsWith('sprite/') ? path : `sprite/${path}`;
    return { url: `/api/v1/pvf/img?path=${encodeURIComponent(spritePath)}&index=${encodeURIComponent(mark.index ?? 0)}&frame=true&v=${encodeURIComponent(imagePackRevision.value)}` };
  });
});
const handleMarkError = url => {
  failedMarkUrls.value = new Set([...failedMarkUrls.value, url]);
};

// 决定是否显示主图片
const shouldShowImage = computed(() => {
  // 必须有路径 且 没有报错
  return props.icon && props.icon.path && !isLoadError.value;
});

// 计算边框颜色类
const rarityClass = computed(() => `rarity-${props.rarity}`);

// 图片加载失败回调
const handleError = () => {
  isLoadError.value = true;
};
</script>

<style scoped>
/* 基础容器 */
.dnf-icon-wrapper {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: 4px; /* 微微圆角 */
  background-color: #1a1a1a; /* 深色底，衬托图标 */
  box-sizing: border-box;
  border: 2px solid transparent; /* 默认边框占位 */
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  vertical-align: middle;
  user-select: none;
}

/* 图片通用样式 */
.dnf-icon-wrapper img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.dnf-icon-wrapper .item-icon-mark {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: fill;
  pointer-events: none;
}

/* 红叉图标稍微缩小一点，避免贴边 */
.error-icon {
  padding: 4px;
  box-sizing: border-box;
  opacity: 0.8;
}

/* --- 品级颜色定义 --- */

/* 0=普通: 灰色 */
.rarity-0 { border-color: #9e9e9e; }

/* 1=高级: 蓝色 */
.rarity-1 { border-color: #4facfe; }

/* 2=稀有: 紫色 */
.rarity-2 { border-color: rgb(66, 0, 181); }

/* 3=神器: 粉色 */
.rarity-3 { border-color: rgb(219, 0, 253); }

/* 4=史诗: 金色 */
.rarity-4 {
  border-color: #ffc107;
  box-shadow: 0 0 3px rgba(255, 193, 7, 0.3);
}

/* 5=勇者: 橙色 */
.rarity-5 { border-color: #ff9800; }

/* 6=神话: 彩色 (流光特效) */
.rarity-6 {
  background: linear-gradient(124deg,#ff2400, #e8b71d, #1de840, #1ddde8, #dd00f3);
}

</style>
