<script setup lang="ts">
import { computed } from 'vue'
import { channelLabel } from './catalog'

const props = withDefaults(defineProps<{ number: number; size?: number }>(), { size: 40 })
const imageStyle = computed(() => {
  const icon = channelLabel(props.number).icon
  const dimensions = { width: `${props.size}px`, height: `${props.size}px`, fontSize: `${props.size * 0.55}px` }
  if (!icon) return dimensions
  const scale = props.size / (icon.size ?? 64)
  return { ...dimensions,
    backgroundImage: `url(/channel-icons/page-${icon.image}.png)`,
    backgroundSize: `${icon.width * scale}px auto`,
    backgroundPosition: `${-icon.x * scale}px ${-icon.y * scale}px`,
  }
})
const hasImage = computed(() => !!channelLabel(props.number).icon)
</script>

<template>
  <span class="channel-icon" :style="imageStyle" aria-hidden="true">{{ hasImage ? '' : '⚔' }}</span>
</template>

<style scoped>
.channel-icon { display: inline-flex; flex: none; border-radius: 5px; background-color: var(--color-fill-3); background-repeat: no-repeat; align-items: center; justify-content: center; }
</style>
