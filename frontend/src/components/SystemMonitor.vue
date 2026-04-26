<template>
  <div class="system-monitor">
    <el-card class="box-card">
      <div slot="header" class="clearfix">
        <span>可视化监控列表</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="fetchDeployments">刷新列表</el-button>
      </div>

      <el-table :data="deployments" style="width: 100%" stripe>
        <el-table-column label="部署名称" prop="name"></el-table-column>
        <el-table-column label="操作" width="150">
          <template slot-scope="scope">
            <el-button 
              type="primary" 
              icon="el-icon-monitor" 
              size="small" 
              @click="openMonitor(scope.row.name)">
              查看监控
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      :title="`监控面板 - ${currentDeploymentName}`"
      :visible.sync="dialogVisible"
      fullscreen
      custom-class="monitor-dialog">
      
      <div v-if="loadingUrls" class="loading-area">
        <i class="el-icon-loading"></i> 正在获取监控地址...
      </div>

      <div v-else-if="urls.length === 0" class="empty-area">
        <el-empty description="当前部署暂无前端页面"></el-empty>
      </div>

      <div v-else class="monitor-container">
        
        <div v-if="maximizedUrl" class="maximized-view">
          <div class="toolbar">
            <el-button icon="el-icon-back" @click="maximizedUrl = null">返回多窗视图</el-button>
            <span class="url-text">当前查看: {{ maximizedUrl }}</span>
          </div>
          <iframe :src="maximizedUrl" class="monitor-iframe full-iframe"></iframe>
        </div>

        <div v-else class="grid-view">
          <div v-for="(url, index) in urls" :key="index" class="grid-item">
            <div class="grid-header">
              <span>窗口 {{ index + 1 }}</span>
            </div>
            <div class="iframe-wrapper" @click="maximize(url)">
              <div class="click-mask">
                <i class="el-icon-zoom-in"></i> 点击放大
              </div>
              <iframe :src="url" class="monitor-iframe mini-iframe"></iframe>
            </div>
          </div>
        </div>

      </div>
    </el-dialog>
  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'SystemMonitor',
  data() {
    return {
      deployments: [],
      dialogVisible: false,
      loadingUrls: false,
      currentDeploymentName: '',
      urls: [],          // 存储从接口获取的 URL 列表
      maximizedUrl: null // 当前被放大的 URL，如果为 null 则显示网格
    };
  },
  mounted() {
    this.fetchDeployments();
  },
  methods: {
    // 1. 获取部署列表 (复用原有接口)
    async fetchDeployments() {
      try {
        const res = await axios.get('/docker/list');
        this.deployments = res.data.map(name => ({ name }));
      } catch (error) {
        this.$message.error('获取列表失败: ' + error.message);
      }
    },

    // 2. 打开监控面板并获取 URL
    async openMonitor(name) {
      this.currentDeploymentName = name;
      this.dialogVisible = true;
      this.loadingUrls = true;
      this.urls = [];
      this.maximizedUrl = null; // 重置为网格视图

      try {
        // 调用新接口获取 List<String>
        const res = await axios.get(`/docker/getUrlLists/${name}`);
        this.urls = res.data || [];
      } catch (error) {
        this.$message.error('获取监控地址失败: ' + (error.response?.data || error.message));
      } finally {
        this.loadingUrls = false;
      }
    },

    // 3. 切换为大窗
    maximize(url) {
      this.maximizedUrl = url;
    }
  }
}
</script>

<style scoped>
.loading-area, .empty-area {
  text-align: center;
  padding: 50px;
  font-size: 16px;
}

/* 网格布局 */
.grid-view {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding: 20px;
  justify-content: center;
  background-color: #f0f2f5;
  min-height: 80vh;
}

.grid-item {
  width: 400px;
  height: 300px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: transform 0.2s;
}

.grid-item:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 15px rgba(0,0,0,0.2);
}

.grid-header {
  padding: 10px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-weight: bold;
  font-size: 14px;
}

.iframe-wrapper {
  position: relative;
  flex: 1;
  cursor: pointer;
}

/* 小窗 iframe */
.mini-iframe {
  width: 100%;
  height: 100%;
  border: none;
  /* 缩放 iframe 内容以适应小窗口 (可选) */
  /* transform: scale(0.5); transform-origin: 0 0; width: 200%; height: 200%; */
}

/* 遮罩层 - 关键点 */
.click-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0); /* 初始透明 */
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
  transition: background 0.3s;
  z-index: 10;
}

.iframe-wrapper:hover .click-mask {
  background: rgba(0, 0, 0, 0.3); /* 悬停时变暗 */
}

.click-mask i, .click-mask span {
  opacity: 0;
  transition: opacity 0.3s;
}

.iframe-wrapper:hover .click-mask i, 
.iframe-wrapper:hover .click-mask span {
  opacity: 1;
}

/* 大窗布局 */
.maximized-view {
  display: flex;
  flex-direction: column;
  height: 100%; /* dialog body height */
  min-height: 85vh;
}

.toolbar {
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid #dcdfe6;
  display: flex;
  align-items: center;
}

.url-text {
  margin-left: 20px;
  color: #909399;
  font-size: 12px;
}

.full-iframe {
  flex: 1;
  width: 100%;
  border: none;
}
</style>