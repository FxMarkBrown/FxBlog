<template>
  <div class="not-found">
    <div class="not-found-content">
      <h1>404</h1>
      <p>抱歉，页面不见了 (；′⌒`)</p>
      <NuxtLink to="/" class="back-home">
        <i class="fas fa-home"></i>
        返回首页
      </NuxtLink>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables.scss' as *;
@use '@/styles/mixins.scss' as *;

.not-found {
  display: flex;
  min-height: calc(100vh - 140px);
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  background: var(--background);
  text-align: center;
}

.not-found-content {
  width: 100%;
  max-width: 600px;
  animation: fade-in-up 0.6s ease-out;

  h1 {
    margin-bottom: 24px;
    color: var(--primary-color, #409eff);
    font-size: 120px;
    font-weight: 700;
    line-height: 1;
    text-shadow: 4px 4px 0 rgba(64, 158, 255, 0.2);
  }

  p {
    margin-bottom: 32px;
    color: var(--text-secondary);
    font-size: 1.5rem;
  }
}

.back-home {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 28px;
  border-radius: 25px;
  background: var(--primary-color, #409eff);
  color: #fff;
  font-weight: 500;
  text-decoration: none;
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
  }
}

@keyframes fade-in-up {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@include responsive(sm) {
  .not-found-content {
    h1 {
      font-size: 80px;
    }

    p {
      font-size: 1.2rem;
    }
  }
}
</style>
