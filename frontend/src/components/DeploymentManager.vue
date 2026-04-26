<template>
  <div class="deployment-manager">
    <el-card class="box-card upload-section">
      <div slot="header" class="clearfix">
        <span>新建部署上传</span>
      </div>
      <el-form :inline="true" class="demo-form-inline">
        <el-form-item label="部署名称">
          <el-input v-model="uploadForm.name" placeholder="输入部署名称 (英文)"></el-input>
        </el-form-item>
        
        <el-form-item label="Tar 镜像包">
          <input type="file" ref="tarInput" accept=".tar" class="custom-file-input"/>
        </el-form-item>

        <el-form-item label="Config 文件">
          <input type="file" ref="configInput" accept=".yaml,.yml" class="custom-file-input"/>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleUpload" :loading="uploading">上传并部署</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="box-card list-section">
      <div slot="header">
        <span>已上传部署列表</span>
        <el-button style="float: right; padding: 3px 0" type="text" @click="fetchDeployments">刷新列表</el-button>
      </div>

      <el-table
        :data="deployments"
        style="width: 100%"
        @expand-change="handleExpandChange">
        
        <el-table-column type="expand">
          <template slot-scope="props">
            <div class="nested-container-list">
              <h4>部署 [{{ props.row.name }}] 的容器状态：</h4>
              
              <div v-if="props.row.loading">正在加载容器状态...</div>

              <el-table
                v-else
                :data="props.row.containers"
                border
                size="mini"
                style="width: 100%">
                
                <el-table-column prop="name" label="容器名称 (Service Name)" width="200"></el-table-column>
                <el-table-column prop="state" label="运行状态" width="120">
                   <template slot-scope="scope">
                     <el-tag :type="scope.row.state === 'running' ? 'success' : 'danger'">
                       {{ scope.row.state }}
                     </el-tag>
                   </template>
                </el-table-column>
                <el-table-column prop="status" label="状态描述"></el-table-column>
                
                <el-table-column label="操作" width="200">
                  <template slot-scope="scope">
                    <el-button size="mini" type="info" icon="el-icon-view" @click="showContainerDetails(scope.row)">详情</el-button>
                    <el-button size="mini" type="danger" icon="el-icon-delete" @click="removeContainer(scope.row.name, props.row.name)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          label="部署名称 (Deployment Name)"
          prop="name">
        </el-table-column>

        <el-table-column label="部署操作" width="300">
          <template slot-scope="scope">
            <el-button size="small" type="success" @click="startDeployment(scope.row.name)">启动部署</el-button>
            <el-button size="small" type="warning" @click="stopDeployment(scope.row.name)">停止部署</el-button>
            <el-button size="small" type="danger" @click="deleteDeployment(scope.row.name)">删除部署</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      title="容器详细状态"
      :visible.sync="detailsVisible"
      width="50%">
      <div v-if="currentContainer">
        <el-descriptions title="基础信息" :column="1" border>
          <el-descriptions-item v-for="(value, key) in currentContainer" :key="key" :label="key">
            {{ value }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="detailsVisible = false">关 闭</el-button>
      </span>
    </el-dialog>

  </div>
</template>

<script>
import axios from 'axios';

export default {
  name: 'DeploymentManager',
  data() {
    return {
      // 部署列表，结构：[{ name: 'hello', containers: [], loading: false }, ...]
      deployments: [],
      uploadForm: {
        name: ''
      },
      uploading: false,
      // 详情弹窗控制
      detailsVisible: false,
      currentContainer: null
    };
  },
  mounted() {
    this.fetchDeployments();
  },
  methods: {
    // 1. 获取部署列表 /docker/list
    async fetchDeployments() {
      try {
        const res = await axios.get('/docker/list');
        // 将简单的字符串数组转换为对象数组，以便存储每个部署的容器状态
        this.deployments = res.data.map(name => ({
          name: name,
          containers: [], // 存放该部署下的容器
          loading: false  // 控制展开时的加载状态
        }));
      } catch (error) {
        this.$message.error('获取部署列表失败: ' + error.message);
      }
    },

    // 2. 展开行时获取容器状态 /docker/status/{name}
    async handleExpandChange(row, expandedRows) {
      // 判断当前是展开还是折叠
      const isExpanded = expandedRows.some(r => r.name === row.name);
      
      if (isExpanded) {
        row.loading = true;
        try {
          const res = await axios.get(`/docker/status/${row.name}`);
          // 这里的 res.data 是你描述的 List<ContainerStatusDTO>
          // Vue 2 的响应式陷阱：直接修改数组内的对象属性需要注意
          row.containers = res.data; 
        } catch (error) {
          this.$message.error(`获取部署 [${row.name}] 状态失败`);
          console.error(error);
        } finally {
          row.loading = false;
        }
      }
    },

    // 3. 上传文件 /upload/deployment
    async handleUpload() {
      const tarFile = this.$refs.tarInput.files[0];
      const configFile = this.$refs.configInput.files[0];
      const name = this.uploadForm.name;

      if (!tarFile || !configFile || !name) {
        this.$message.warning('请填写名称并选择两个文件');
        return;
      }

      const formData = new FormData();
      formData.append('tarFile', tarFile);
      formData.append('configFile', configFile);
      formData.append('name', name);

      this.uploading = true;
      try {
        await axios.post('/upload/deployment', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        this.$message.success('上传并部署成功！');
        // 清空表单
        this.uploadForm.name = '';
        this.$refs.tarInput.value = '';
        this.$refs.configInput.value = '';
        // 刷新列表
        this.fetchDeployments();
      } catch (error) {
        this.$message.error('上传失败: ' + (error.response?.data || error.message));
      } finally {
        this.uploading = false;
      }
    },

    // 4. 启动部署 /docker/start/{name}
    async startDeployment(name) {
      try {
        const res = await axios.post(`/docker/start/${name}`);
        this.$message.success(res.data);
        // 刷新该行的状态（如果已展开，可以通过重新触发获取逻辑，这里简单起见提示用户）
      } catch (error) {
        this.$message.error('启动失败: ' + (error.response?.data || error.message));
      }
    },

    // 5. 停止部署 /docker/stop/{deploymentName}
    async stopDeployment(name) {
      try {
        const res = await axios.post(`/docker/stop/${name}`);
        this.$message.success(res.data);
      } catch (error) {
        this.$message.error('停止失败: ' + (error.response?.data || error.message));
      }
    },
    // 删除部署 /docker/stop/{deploymentName}
    async deleteDeployment(name) {
      try {
        const res = await axios.post(`/upload/delete/${name}`);
        this.$message.success(res.data);
        // 刷新该行的状态（如果已展开，可以通过重新触发获取逻辑，这里简单起见提示用户）
      } catch (error) {
        this.$message.error('删除失败: ' + (error.response?.data || error.message));
      }
    },

    // 6. 删除单个容器 /docker/remove/{serviceName}
    async removeContainer(serviceName, deploymentName) {
      this.$confirm(`确认删除容器 ${serviceName}?`, '提示', {
        type: 'warning'
      }).then(async () => {
        try {
          await axios.delete(`/docker/remove/${serviceName}`);
          this.$message.success('容器已删除');
          // 简单的刷新状态逻辑：找到对应的部署对象并重新获取状态
          const deployment = this.deployments.find(d => d.name === deploymentName);
          if (deployment) {
            this.handleExpandChange(deployment, [deployment]); // 模拟展开刷新
          }
        } catch (error) {
          this.$message.error('删除失败: ' + (error.response?.data || error.message));
        }
      });
    },

    // 7. 显示详情弹窗
    showContainerDetails(container) {
      this.currentContainer = container;
      this.detailsVisible = true;
    }
  }
}
</script>

<style scoped>
.upload-section {
  margin-bottom: 20px;
}
.custom-file-input {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 5px;
  width: 250px;
}
.nested-container-list {
  padding: 10px 20px;
  background-color: #f9fafc;
  border-radius: 4px;
}
</style>