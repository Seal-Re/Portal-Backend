package com.seal.portalbackend.ett;

import java.util.List;
import java.util.Map;

/**
 * 对应 config.yaml 文件的根结构
 */
public class PortalConfig {
    private String version;
    private String application_id;
    private List<ImageConfig> images;
    private List<FrontendConfig> frontend;

    // Getters and Setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getApplication_id() { return application_id; }
    public void setApplication_id(String application_id) { this.application_id = application_id; }

    public List<ImageConfig> getImages() { return images; }
    public void setImages(List<ImageConfig> images) { this.images = images; }

    public List<FrontendConfig> getFrontend() { return frontend; }
    public void setFrontend(List<FrontendConfig> frontend) { this.frontend = frontend; }

    /**
     * 单个服务或镜像配置
     */
    public static class ImageConfig {
        private String name;           // 镜像名称
        private String service_name;   // 容器名称
        private String description;
        private List<PortMapping> ports;
        private List<String> volumes;  // 卷挂载配置列表
        private Map<String, String> environment; // 环境变量
        private List<String> depends_on; // 启动依赖

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getService_name() { return service_name; }
        public void setService_name(String service_name) { this.service_name = service_name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<PortMapping> getPorts() { return ports; }
        public void setPorts(List<PortMapping> ports) { this.ports = ports; }

        public List<String> getVolumes() { return volumes; }
        public void setVolumes(List<String> volumes) { this.volumes = volumes; }

        public Map<String, String> getEnvironment() { return environment; }
        public void setEnvironment(Map<String, String> environment) { this.environment = environment; }

        public List<String> getDepends_on() { return depends_on; }
        public void setDepends_on(List<String> depends_on) { this.depends_on = depends_on; }
    }

    /**
     * 端口映射结构
     */
    public static class PortMapping {
        private int host;       // 宿主机端口
        private int container;  // 容器端口

        // Getters and Setters
        public int getHost() { return host; }
        public void setHost(int host) { this.host = host; }

        public int getContainer() { return container; }
        public void setContainer(int container) { this.container = container; }
    }

    /**
     * 前端url结构
     */
    public static class FrontendConfig {
        private String url;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}