import { ref } from 'vue';

// A new URL retries images that were missing before the resource pack was uploaded.
export const imagePackRevision = ref(String(Date.now()));

export function refreshImagePackIcons(revision: string) {
  imagePackRevision.value = `${revision}-${Date.now()}`;
}
