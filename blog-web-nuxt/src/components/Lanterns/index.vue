<script setup lang="ts">
const siteStore = useSiteStore()
</script>

<template>
  <div v-if="siteStore.websiteInfo.openLantern" class="app1">
    <div class="lantern-box">
      <div class="lantern-left">
        <div v-for="text in ['新', '年']" :key="text" class="lantern">
          <div class="lines"></div>
          <div class="lantern-a">
            <div class="lantern-b">
              <div class="lantern-t">{{ text }}</div>
            </div>
          </div>
          <div class="spike spike-a">
            <div class="spike-c"></div>
            <div class="spike-b"></div>
          </div>
        </div>
      </div>
      <div class="lantern-right">
        <div v-for="text in ['快', '乐']" :key="text" class="lantern">
          <div class="lines"></div>
          <div class="lantern-a">
            <div class="lantern-b">
              <div class="lantern-t">{{ text }}</div>
            </div>
          </div>
          <div class="spike spike-a">
            <div class="spike-c"></div>
            <div class="spike-b"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lantern-box {
  position: relative;
  top: 0;
  z-index: 999;
  pointer-events: none;
}

.lantern-left {
  position: fixed;
  left: 40px;
  top: 20px;
}

.lantern-right {
  position: fixed;
  right: 40px;
  top: 20px;
}

.lantern {
  position: relative;
  width: 120px;
  height: 90px;
  background: rgba(216, 0, 15, 0.8);
  border-radius: 50% 50%;
  transform-origin: 50% -100px;
  animation: swing 3s infinite ease-in-out;
  box-shadow: -5px 5px 50px 4px rgba(250, 108, 0, 1);
  display: inline-block;
  margin-right: 10px;
}

.lantern-a {
  width: 100px;
  height: 90px;
  background: rgba(216, 0, 15, 0.1);
  margin: 0 8px 8px 8px;
  border-radius: 50% 50%;
  border: 2px solid #dc8f03;
}

.lantern-b {
  width: 50px;
  height: 90px;
  background: rgba(216, 0, 15, 0.1);
  margin: -4px 8px 8px 26px;
  border-radius: 50% 50%;
  border: 2px solid #dc8f03;
}

.lines {
  position: absolute;
  top: -20px;
  left: 60px;
  width: 3px;
  height: 20px;
  background: #dc8f03;
}

.spike-a {
  position: relative;
  width: 5px;
  height: 20px;
  margin: -5px 0 0 59px;
  animation: swing 4s infinite ease-in-out;
  background: #ffa500;
  border-radius: 0 0 5px 5px;
}

.spike-b {
  position: absolute;
  top: 14px;
  left: -2px;
  width: 10px;
  height: 10px;
  background: #dc8f03;
  border-radius: 50%;
}

.spike-c {
  position: absolute;
  top: 18px;
  left: -2px;
  width: 10px;
  height: 35px;
  background: #ffa500;
}

.lantern-t {
  font-size: 3.2rem;
  color: #dc8f03;
  font-weight: bold;
  line-height: 85px;
  text-align: center;
}

@keyframes swing {
  0% {
    transform: rotate(-10deg);
  }
  50% {
    transform: rotate(10deg);
  }
  100% {
    transform: rotate(-10deg);
  }
}
</style>
