<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { getAlbumListApi } from '@/api/album'
import type { AlbumSummary } from '@/types/article'
import { IMAGE_ERROR_PLACEHOLDER } from '@/utils/placeholders'
import { unwrapResponseData } from '@/utils/response'

const router = useRouter()
const runtimeConfig = useRuntimeConfig()
const albums = ref<Array<AlbumSummary & { id: number | string; name: string; description: string; cover: string; photoNum: number; isLock: number }>>([])

useSeoMeta({
  title: () => `相册 - ${runtimeConfig.public.siteName}`,
  description: '每一张照片都是生活中美好的一次记忆'
})

await getAlbumList()

function normalizeAlbums(records: AlbumSummary[]) {
  return records.map((album, index) => ({
    ...album,
    id: album.id || `album-${index}`,
    name: String(album.name || '未命名相册'),
    description: String(album.description || ''),
    cover: String(album.cover || IMAGE_ERROR_PLACEHOLDER),
    photoNum: Number(album.photoNum || 0),
    isLock: Number(album.isLock || 0)
  }))
}

async function getAlbumList() {
  const response = await getAlbumListApi().catch(() => null)

  if (!response) {
    albums.value = []
    showError('获取相册列表失败')
    return
  }

  albums.value = normalizeAlbums(unwrapResponseData<AlbumSummary[] | null>(response) || [])
}

function handleCoverError(event: Event) {
  const target = event.target as HTMLImageElement
  target.src = IMAGE_ERROR_PLACEHOLDER
}

function openAlbum(album: { id: number | string }) {
  router.push(`/photos/${album.id}`)
}

function showError(message: string) {
  if (import.meta.client) {
    ElMessage.error(message)
  }
}
</script>

<template>
  <div class="photos-container">
    <div class="photos-header">
      <div class="header-content">
        <div class="title-group">
          <h1>相册</h1>
          <div class="decorative-line"></div>
        </div>
        <p class="subtitle">每一张照片都是生活中美好的一次记忆</p>
      </div>
      <div class="header-background">
        <div class="circle circle-1"></div>
        <div class="circle circle-2"></div>
      </div>
    </div>
    <div class="photos-grid">
      <div v-for="album in albums" :key="album.id" class="album-card" @click="openAlbum(album)">
        <div class="album-cover">
          <img :src="album.cover" :alt="album.name" @error="handleCoverError">
          <div v-if="album.isLock === 1" class="lock-icon">
            <i class="fas fa-lock"></i>
          </div>
        </div>
        <div class="album-info">
          <h3>{{ album.name }}</h3>
          <p>{{ album.description }}</p>
          <span class="photo-count">{{ album.photoNum }}张照片</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.photos-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.photos-header {
  position: relative;
  text-align: center;
  margin-bottom: 60px;
  padding: 40px 20px;
  overflow: hidden;

  .header-content {
    position: relative;
    z-index: 2;
  }

  .title-group {
    display: inline-flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: 20px;
  }

  h1 {
    font-size: 3em;
    font-weight: 700;
    margin-bottom: 15px;
    background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #ec4899 100%);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
    letter-spacing: 2px;
  }

  .decorative-line {
    width: 60px;
    height: 4px;
    background: linear-gradient(90deg, #6366f1, #8b5cf6);
    border-radius: 2px;
    margin-top: -5px;
  }

  .subtitle {
    font-size: 1.2em;
    color: var(--text-secondary);
    font-weight: 500;
    letter-spacing: 1px;
    opacity: 0.8;
  }

  .header-background {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 1;
    overflow: hidden;
  }

  .circle {
    position: absolute;
    border-radius: 50%;
    opacity: 0.1;
  }

  .circle-1 {
    width: 200px;
    height: 200px;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    top: -100px;
    left: -50px;
  }

  .circle-2 {
    width: 150px;
    height: 150px;
    background: linear-gradient(135deg, #8b5cf6, #ec4899);
    bottom: -50px;
    right: -30px;
  }
}

.photos-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.album-card {
  background: var(--surface);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  transition: all 0.3s ease;
  cursor: pointer;

  &:hover {
    transform: translateY(-5px);
    box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);

    .album-cover img {
      transform: scale(1.05);
    }
  }

  .album-cover {
    height: 200px;
    overflow: hidden;
    position: relative;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.3s ease;
    }

    .lock-icon {
      position: absolute;
      top: 10px;
      right: 10px;
      width: 32px;
      height: 32px;
      background: rgba(0, 0, 0, 0.5);
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      font-size: 14px;
    }
  }

  .album-info {
    padding: 20px;

    h3 {
      margin: 0 0 10px;
      font-size: 1.2em;
      color: var(--text-primary);
    }

    p {
      color: var(--text-secondary);
      margin: 0 0 15px;
      font-size: 0.9em;
      line-height: 1.5;
    }

    .photo-count {
      display: inline-block;
      padding: 4px 12px;
      background: rgba(99, 102, 241, 0.1);
      color: #6366f1;
      border-radius: 20px;
      font-size: 0.8em;
      font-weight: 500;
    }
  }
}

@media (max-width: 768px) {
  .photos-header {
    padding: 30px 15px;
    margin-bottom: 40px;

    h1 {
      font-size: 2.5em;
    }

    .subtitle {
      font-size: 1.1em;
    }
  }

  .photos-grid {
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 15px;
  }
}

@media (max-width: 480px) {
  .photos-header {
    padding: 25px 10px;
    margin-bottom: 30px;

    h1 {
      font-size: 2em;
    }

    .subtitle {
      font-size: 1em;
    }

    .circle-1 {
      width: 150px;
      height: 150px;
    }

    .circle-2 {
      width: 100px;
      height: 100px;
    }
  }

  .photos-grid {
    grid-template-columns: 1fr;
  }
}
</style>
