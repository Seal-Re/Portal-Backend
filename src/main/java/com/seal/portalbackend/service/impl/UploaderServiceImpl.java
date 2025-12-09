package com.seal.portalbackend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seal.portalbackend.ett.PortalConfig;
import com.seal.portalbackend.service.DockerService;
import com.seal.portalbackend.service.UploaderService;
import com.seal.portalbackend.utils.CommonConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
public class UploaderServiceImpl implements UploaderService {

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
    private DockerService dockerService;

    @Autowired
    @Qualifier("yamlMapper")
    private ObjectMapper yamlMapper;

    private Logger LOG = LoggerFactory.getLogger(UploaderServiceImpl.class);

    @Override
    public ResponseEntity<String> uploadDeploymentFiles(MultipartFile tarFile, MultipartFile configFile, String name) {
        if (tarFile.isEmpty() || configFile.isEmpty() || name.isBlank()) {
            return ResponseEntity.badRequest().body("缺少必要的文件或部署名称 (name) 参数。");
        }

        try {
            Path targetDir = Paths.get(BASE_DIR, name);
            Files.createDirectories(targetDir);
            LOG.info("创建目录: " + targetDir);

            // 1. 存储 Tar 文件
            String tarFileName = tarFile.getOriginalFilename();
            Path tarFilePath = targetDir.resolve(tarFileName);
            Files.copy(tarFile.getInputStream(), tarFilePath, StandardCopyOption.REPLACE_EXISTING);

            // 2. 读取 Config 文件 (先暂时不保存到磁盘，只读入内存)
            PortalConfig config = yamlMapper.readValue(configFile.getInputStream(), PortalConfig.class);

            // 3. 【核心步骤】先加载镜像 (此时 Docker 中存在的是原名镜像)
            LOG.info("开始加载镜像 (原名)...");
            dockerService.loadImage(name, tarFileName);

            // 4. 【核心步骤】遍历 Config，根据原名去 Docker 里重命名，并更新 Config 对象
            String prefix = name.toLowerCase(); // 部署名前缀 (强制小写)

            if (config.getImages() != null) {
                for (PortalConfig.ImageConfig imageConfig : config.getImages()) {
                    String originalImageName = imageConfig.getName();           // e.g., nginx:latest
                    String originalServiceName = imageConfig.getService_name(); // e.g., my-web

                    // 构造新名称
                    String newImageName = prefix + "_" + originalImageName;       // e.g., deploy_nginx:latest
                    String newServiceName = prefix + "_" + originalServiceName;   // e.g., deploy_my-web

                    LOG.info("正在处理镜像重命名: [{}] -> [{}]", originalImageName, newImageName);

                    try {
                        // 调用 DockerService 执行重命名 (Tag New -> Remove Old)
                        dockerService.renameImage(originalImageName, newImageName);

                        // 重命名成功后，更新 Config 内存对象
                        imageConfig.setName(newImageName);
                        imageConfig.setService_name(newServiceName);

                    } catch (Exception e) {
                        LOG.error("镜像重命名失败! 请检查 config.yaml 中的 image 名称 '{}' 是否与 tar 包中的一致。", originalImageName);
                        throw new IOException("镜像重命名失败: " + e.getMessage());
                    }
                }
            }

            // 5. 将修改后的 Config 保存到磁盘 (config.yaml)
            // 此时保存的文件里，image 已经是带前缀的新名字了，供后续 StartDeployment 使用
            Path configFilePath = targetDir.resolve("config.yaml");
            yamlMapper.writeValue(configFilePath.toFile(), config);
            LOG.info("已保存修改后的配置文件: config.yaml");

            return ResponseEntity.ok("部署上传并处理成功。镜像已重命名，配置已更新。");

        } catch (IOException e) {
            LOG.error("上传流程失败: " + e.getMessage());
            e.printStackTrace();
            // 建议：如果失败，可以尝试清理已上传的文件
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("上传处理失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<String> deleteDeploymentFiles(String name) {
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body("缺少部署名称 (name) 参数。");
        }

        Path targetDir = Paths.get(BASE_DIR, name);

        if (!Files.exists(targetDir)) {
            try {
                // 目录不存在也尝试清理一下 Docker，防止有残留容器
                dockerService.stopAndRemove(name);
            } catch (Exception e) {
                LOG.warn("清理 Docker 资源出错 (目录不存在场景): " + e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("未找到名为 '" + name + "' 的部署文件目录。");
        }

        try {
            // 1. 先清理 Docker 资源 (停止容器 -> 删除容器 -> 删除镜像)
            LOG.info("开始清理部署 '" + name + "' 的 Docker 资源...");
            dockerService.stopAndRemove(name);
            LOG.info("Docker 资源清理完成。");

            // 2. 递归删除文件目录
            try (Stream<Path> walk = Files.walk(targetDir)) {
                walk.sorted(Comparator.reverseOrder()) // 倒序：先删子节点
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }

            LOG.info("已成功删除文件目录: " + targetDir);
            return ResponseEntity.ok("部署 '" + name + "' 已下线，相关容器及文件已清除。");

        } catch (IOException e) {
            LOG.error("删除文件失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("容器可能已清理，但文件删除失败: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Docker 资源清理失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Docker 资源清理失败，文件未删除: " + e.getMessage());
        }
    }
}