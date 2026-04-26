<template>
  <div id="app">
    <div class="sidebar">
      <div class="logo">门户管理平台</div>
      <el-menu
        :default-active="activeIndex"
        class="el-menu-vertical-demo"
        background-color="#545c64"
        text-color="#fff"
        active-text-color="#ffd04b"
        @select="handleSelect">
        
        <el-menu-item index="1">
          <i class="el-icon-menu"></i>
          <span slot="title">部署管理</span>
        </el-menu-item>
        
        <el-menu-item index="2">
          <i class="el-icon-setting"></i>
          <span slot="title">系统监控</span>
        </el-menu-item>
      </el-menu>
    </div>

    <div class="main-content">
      <div class="header">
        <h2>{{ pageTitle }}</h2>
      </div>

      <div class="content-body">
        <DeploymentManager v-if="activeIndex === '1'" />
        <SystemMonitor v-if="activeIndex === '2'" />
      </div>
    </div>
  </div>
</template>

<script>
import DeploymentManager from './components/DeploymentManager.vue'
import SystemMonitor from './components/SystemMonitor.vue' // 引入新组件

export default {
  name: 'App',
  components: {
    DeploymentManager,
    SystemMonitor
  },
  data() {
    return {
      activeIndex: '1' // 默认显示第一个页面
    };
  },
  computed: {
    pageTitle() {
      return this.activeIndex === '1' ? '容器部署控制台' : '可视化监控中心';
    }
  },
  methods: {
    /* eslint-disable-next-line no-unused-vars */
    handleSelect(key, _) { 
      this.activeIndex = key;
    }
  }
}
</script>

<style>
/* 保持原有样式不变 */
body {
  margin: 0;
  padding: 0;
  font-family: "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "微软雅黑", Arial, sans-serif;
  background-color: #f0f2f5;
}

#app {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 240px;
  background-color: #545c64;
  height: 100%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
  background-color: #434a50;
}

.main-content {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  height: 60px;
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);
}

.content-body {
  flex-grow: 1;
  padding: 20px;
  overflow-y: auto;
}
</style>