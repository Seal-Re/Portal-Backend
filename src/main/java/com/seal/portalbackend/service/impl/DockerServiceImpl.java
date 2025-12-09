package com.seal.portalbackend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.seal.portalbackend.ett.PortalConfig;
import com.seal.portalbackend.module.ContainerStatusDTO;
import com.seal.portalbackend.service.DockerService;
import com.seal.portalbackend.utils.CommonConstants;
import com.seal.portalbackend.utils.DockerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DockerServiceImpl implements DockerService {

    private static String BASE_DIR;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            BASE_DIR = CommonConstants.BASE_DIR_WINDOWS;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            BASE_DIR = CommonConstants.BASE_DIR_LINUX;
        } else if (osName.contains("mac")) {
            BASE_DIR = CommonConstants.BASE_DIR_LINUX;
        } else {
            BASE_DIR = CommonConstants.BASE_DIR_LINUX;
        }
    }

    @Autowired
    private DockerClient dockerClient;

    private final ObjectMapper yamlMapper;

    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Autowired
    public DockerServiceImpl(DockerClient dockerClient, @Qualifier("yamlMapper") ObjectMapper yamlMapper) {
        this.dockerClient = dockerClient;
        this.yamlMapper = yamlMapper;
    }

    @Override
    public PortalConfig readConfig(String deploymentName) throws IOException {
        Path configPath = Paths.get(BASE_DIR, deploymentName, "config.yaml");
        if (!Files.exists(configPath)) {
            throw new IOException("找不到部署配置文件: " + configPath.toString());
        }
        return yamlMapper.readValue(configPath.toFile(), PortalConfig.class);
    }

    @Override
    public void loadImage(String deploymentName, String tarFileName) throws IOException {
        Path tarFilePath = Paths.get(BASE_DIR, deploymentName, tarFileName);
        LOG.info("正在导入镜像文件: {}", tarFilePath);

        if (!Files.exists(tarFilePath)) {
            throw new IOException("镜像文件未找到: " + tarFilePath);
        }

        try (InputStream inputStream = Files.newInputStream(tarFilePath)) {
            DockerUtils.loadImage(dockerClient, inputStream);
            LOG.info("镜像加载指令已发送至 Docker 引擎。");
        } catch (IOException e) {
            LOG.error("读取或加载镜像文件失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 加载并重命名镜像 (Tag New -> Remove Old)
     */
    @Override
    public void loadAndRetagImages(String deploymentName, String tarFileName, Map<String, String> imageRenamingMap) throws IOException {
        LOG.info("=== 开始智能加载镜像流程 ===");

        List<Image> beforeImages = dockerClient.listImagesCmd().exec();
        List<String> beforeImageIds = beforeImages.stream()
                .map(Image::getId)
                .collect(Collectors.toList());

        loadImage(deploymentName, tarFileName);

        List<Image> afterImages = dockerClient.listImagesCmd().exec();

        // 找到那个不在 beforeImageIds 里的新镜像
        Image newlyLoadedImage = afterImages.stream()
                .filter(img -> !beforeImageIds.contains(img.getId()))
                .findFirst()
                .orElse(null);

        if (newlyLoadedImage == null) {
            // 如果没找到新ID，说明镜像可能之前已经存在了
            LOG.warn("未检测到新的镜像 ID (可能该镜像已存在)。将尝试使用 Config 中的原始名称进行硬匹配...");
            fallbackRetag(imageRenamingMap);
            return;
        }

        String targetImageId = newlyLoadedImage.getId();
        LOG.info("捕获到新导入的镜像 ID: {}", targetImageId);

        if (imageRenamingMap == null || imageRenamingMap.isEmpty()) {
            return;
        }


        for (Map.Entry<String, String> entry : imageRenamingMap.entrySet()) {
            String newName = entry.getValue().toLowerCase(); // 目标新名 (带前缀)

            String repository;
            String tag;
            if (newName.contains(":")) {
                String[] parts = newName.split(":", 2);
                repository = parts[0];
                tag = parts[1];
            } else {
                repository = newName;
                tag = "latest";
            }

            try {
                dockerClient.tagImageCmd(targetImageId, repository, tag).exec();
                LOG.info("成功给镜像 ID [{}] 打上新标签: {}:{}", targetImageId, repository, tag);

                if (newlyLoadedImage.getRepoTags() != null) {
                    for (String existingTag : newlyLoadedImage.getRepoTags()) {
                        String fullNewTag = repository + ":" + tag;
                        if (!existingTag.equals(fullNewTag)) {
                            try {
                                LOG.info("清理旧的原始标签: {}", existingTag);
                                dockerClient.removeImageCmd(existingTag).exec();
                            } catch (Exception e) {
                                LOG.warn("清理旧标签失败 (可能是由 ID 引用，不影响使用): {}", e.getMessage());
                            }
                        }
                    }
                }

            } catch (Exception e) {
                LOG.error("重命名失败 [Target: {}]: {}", newName, e.getMessage());
                throw new IOException("镜像重命名失败，无法完成部署流程。", e);
            }
        }
    }

    /**
     * 降级方案：如果 ID 没变 (镜像已存在)，则尝试用名字匹配
     */
    private void fallbackRetag(Map<String, String> imageRenamingMap) {
        if (imageRenamingMap == null) return;

        for (Map.Entry<String, String> entry : imageRenamingMap.entrySet()) {
            String originalName = entry.getKey();
            String newName = entry.getValue().toLowerCase();

            String repository = newName.contains(":") ? newName.split(":")[0] : newName;
            String tag = newName.contains(":") ? newName.split(":")[1] : "latest";

            try {
                dockerClient.tagImageCmd(originalName, repository, tag).exec();
                LOG.info("[Fallback] 按名称重命名成功: {} -> {}", originalName, newName);
                try { dockerClient.removeImageCmd(originalName).exec(); } catch (Exception ignored) {}
            } catch (Exception e) {
                LOG.error("[Fallback] 按名称重命名也失败了: {}", e.getMessage());
            }
        }
    }

    @Override
    public List<String> startDeployment(String deploymentName) throws IOException {
        PortalConfig config = readConfig(deploymentName);
        List<String> containerIds = new ArrayList<>();

        for (PortalConfig.ImageConfig imageConfig : config.getImages()) {
            String containerName = imageConfig.getService_name(); // 这里的名字已经是带前缀的了

            // 检查容器是否已存在并尝试清理
            try {
                dockerClient.inspectContainerCmd(containerName).exec();
                LOG.info("容器 " + containerName + " 已存在，先尝试停止和删除...");
                removeContainer(containerName);
            } catch (NotFoundException e) {
                // 忽略
            } catch (Exception e) {
                LOG.error("检查容器状态异常: " + e.getMessage());
                throw new IOException("无法处理现有容器: " + containerName);
            }

            // 端口映射
            List<PortBinding> portBindings = new ArrayList<>();
            List<ExposedPort> exposedPorts = new ArrayList<>();
            if (imageConfig.getPorts() != null) {
                for (PortalConfig.PortMapping pm : imageConfig.getPorts()) {
                    ExposedPort exposedPort = ExposedPort.tcp(pm.getContainer());
                    exposedPorts.add(exposedPort);
                    portBindings.add(new PortBinding(
                            Ports.Binding.bindIpAndPort("0.0.0.0", pm.getHost()),
                            exposedPort
                    ));
                }
            }

            // 卷挂载
            List<Bind> binds = new ArrayList<>();
            if (imageConfig.getVolumes() != null) {
                for (String volume : imageConfig.getVolumes()) {
                    String[] parts = volume.split(":");
                    if (parts.length == 2) {
                        Path hostPath = Paths.get(BASE_DIR, deploymentName, parts[0]);
                        binds.add(new Bind(hostPath.toString(), new Volume(parts[1])));
                    }
                }
            }

            // 环境变量
            List<String> envs = new ArrayList<>();
            if (imageConfig.getEnvironment() != null) {
                for (Map.Entry<String, String> entry : imageConfig.getEnvironment().entrySet()) {
                    envs.add(entry.getKey() + "=" + entry.getValue());
                }
            }

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withPortBindings(new Ports(portBindings.toArray(new PortBinding[0])))
                    .withBinds(binds);

            // 创建容器 (imageConfig.getName() 已经是带前缀的新镜像名)
            CreateContainerResponse container = dockerClient.createContainerCmd(imageConfig.getName())
                    .withName(containerName)
                    .withHostConfig(hostConfig)
                    .withEnv(envs)
                    .withExposedPorts(exposedPorts)
                    .exec();

            containerIds.add(container.getId());
            dockerClient.startContainerCmd(container.getId()).exec();
            LOG.info("容器 " + containerName + " 启动成功。");
        }
        return containerIds;
    }

    @Override
    public List<String> stopDeployment(String deploymentName) throws IOException {
        PortalConfig config = readConfig(deploymentName);
        List<String> stoppedContainers = new ArrayList<>();

        for (PortalConfig.ImageConfig imageConfig : config.getImages()) {
            String containerName = imageConfig.getService_name();
            try {
                dockerClient.stopContainerCmd(containerName).exec();
                stoppedContainers.add(containerName);
            } catch (Exception e) {
                LOG.warn("停止容器 '{}' 失败或容器不存在: {}", containerName, e.getMessage());
            }
        }
        return stoppedContainers;
    }

    @Override
    public void removeContainer(String containerNameOrId) {
        LOG.info("删除容器: " + containerNameOrId);
        try {
            dockerClient.removeContainerCmd(containerNameOrId).withForce(true).exec();
        } catch (NotFoundException e) {
            LOG.debug("容器不存在: " + containerNameOrId);
        } catch (Exception e) {
            LOG.error("删除容器失败: " + e.getMessage());
        }
    }

    @Override
    public void stopAndRemove(String deploymentName) {
        LOG.info("清理部署 '{}' 的 Docker 资源...", deploymentName);
        try {
            // 读取已修改的 config，包含带前缀的名字
            PortalConfig config = readConfig(deploymentName);

            for (PortalConfig.ImageConfig imageConfig : config.getImages()) {
                String containerName = imageConfig.getService_name();
                String imageName = imageConfig.getName();

                // 1. 强删容器
                removeContainer(containerName);

                // 2. 尝试删除镜像 (释放空间)
                removeImage(imageName);
            }
        } catch (IOException e) {
            LOG.warn("无法读取配置，跳过 Docker 清理: {}", e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Docker 资源清理异常: " + e.getMessage());
        }
    }

    private void removeImage(String imageName) {
        try {
            dockerClient.removeImageCmd(imageName).exec();
            LOG.info("镜像 '{}' 删除成功。", imageName);
        } catch (NotFoundException e) {
            LOG.debug("镜像不存在，跳过: {}", imageName);
        } catch (Exception e) {
            LOG.warn("删除镜像 '{}' 失败: {}", imageName, e.getMessage());
        }
    }

    @Override
    public List<ContainerStatusDTO> getDeploymentContainersStatus(String deploymentName) throws IOException {
        PortalConfig config = readConfig(deploymentName);
        List<String> serviceNames = config.getImages().stream()
                .map(PortalConfig.ImageConfig::getService_name)
                .collect(Collectors.toList());

        List<Container> allContainers = dockerClient.listContainersCmd().withShowAll(true).exec();

        return allContainers.stream()
                .filter(c -> {
                    String name = (c.getNames() != null && c.getNames().length > 0) ? c.getNames()[0].replace("/", "") : "";
                    return serviceNames.contains(name);
                })
                .map(this::convertToStatusDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> listDeployments() {
        Path baseDirPath = Paths.get(BASE_DIR);
        if (!Files.exists(baseDirPath) || !Files.isDirectory(baseDirPath)) {
            return Collections.emptyList();
        }
        try (Stream<Path> stream = Files.list(baseDirPath)) {
            return stream.filter(Files::isDirectory)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> !name.startsWith("."))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error("列出部署失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private ContainerStatusDTO convertToStatusDTO(Container container) {
        ContainerStatusDTO dto = new ContainerStatusDTO();
        dto.setId(container.getId().substring(0, 12));
        dto.setImage(container.getImage());
        dto.setStatus(container.getStatus());
        dto.setState(container.getState());

        String fullName = (container.getNames() != null && container.getNames().length > 0)
                ? container.getNames()[0].replace("/", "") : "N/A";
        dto.setName(fullName);

        if (container.getCreated() != null) {
            dto.setCreatedTime(LocalDateTime.ofInstant(Instant.ofEpochSecond(container.getCreated()), ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            dto.setCreatedTime("N/A");
        }

        if (container.getPorts() != null && container.getPorts().length > 0) {
            var p = container.getPorts()[0];
            dto.setHostPort(p.getPublicPort() != null ? String.valueOf(p.getPublicPort()) : "N/A");
            dto.setContainerPort(p.getPrivatePort() != null ? String.valueOf(p.getPrivatePort()) : "N/A");
        } else {
            dto.setHostPort("N/A");
            dto.setContainerPort("N/A");
        }
        return dto;
    }

    @Override
    public void renameImage(String oldImageName, String newImageName) throws IOException {
        // 1. 规范化新名称 (全小写)
        String finalNewName = newImageName.toLowerCase();

        // 解析 Repo 和 Tag
        String repository;
        String tag;
        if (finalNewName.contains(":")) {
            String[] parts = finalNewName.split(":", 2);
            repository = parts[0];
            tag = parts[1];
        } else {
            repository = finalNewName;
            tag = "latest";
        }

        try {
            dockerClient.tagImageCmd(oldImageName, repository, tag).exec();
            LOG.info("镜像打标成功: {} -> {}:{}", oldImageName, repository, tag);

            try {
                dockerClient.removeImageCmd(oldImageName).exec();
                LOG.info("已移除原始旧名: {}", oldImageName);
            } catch (Exception e) {
                LOG.warn("保留了原始镜像名 '{}' (可能被占用)，不影响新镜像使用。", oldImageName);
            }

        } catch (NotFoundException e) {
            throw new IOException("Docker 中找不到名为 '" + oldImageName + "' 的镜像。请确保 config.yaml 中的 image 名称与 tar 包内完全一致！");
        } catch (Exception e) {
            throw new IOException("镜像重命名异常: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getUrlLists(String deploymentName) throws IOException {
        PortalConfig config = readConfig(deploymentName);
        List<PortalConfig.FrontendConfig> frontendConfigs = config.getFrontend();
        List<String> results = new ArrayList<>();
        if (frontendConfigs == null) {
            return results;
        }
        return frontendConfigs.stream()
                .map(PortalConfig.FrontendConfig::getUrl)
                .filter(url -> url != null && !url.isEmpty())
                .collect(Collectors.toList());
    }
}