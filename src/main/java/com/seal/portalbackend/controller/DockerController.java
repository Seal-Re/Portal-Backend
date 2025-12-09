package com.seal.portalbackend.controller;

import com.github.dockerjava.api.model.Container;
import com.seal.portalbackend.module.ContainerStatusDTO;
import com.seal.portalbackend.service.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;

/**
 * 负责处理 Docker 容器管理相关的 HTTP 请求。
 * 调整后，启动和停止操作主要针对部署名称 (name)。
 */
@Slf4j
@RestController
@RequestMapping("/docker")
public class DockerController {

    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    private final DockerService dockerService;

    public DockerController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    /**
     * 启动部署
     * @param name 部署名称
     */
    @PostMapping("/start/{name}")
    public ResponseEntity<String> startDeployment(
            @PathVariable("name") String name) {

        try {

            // 创建并启动所有容器
            List<String> containerIds = dockerService.startDeployment(name);

            return ResponseEntity.ok("部署 '" + name + "' 启动成功。启动的容器ID: " + containerIds);

        } catch (IOException e) {
            LOG.error("部署启动失败 (IO/配置): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("部署启动失败 (配置/文件错误): " + e.getMessage());
        } catch (Exception e) {
            LOG.error("部署启动失败 (Docker操作): " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("部署启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止指定部署下的所有容器
     * 满足用户需求：停止的操作应该使用对应的那个name，来查找对应的文件夹，然后通过config来停止其中所有的镜像
     * @param deploymentName 部署名称
     */
    @PostMapping("/stop/{deploymentName}")
    public ResponseEntity<String> stopDeployment(@PathVariable String deploymentName) {
        try {
            List<String> stoppedContainers = dockerService.stopDeployment(deploymentName);
            if (stoppedContainers.isEmpty()) {
                return ResponseEntity.ok("部署 '" + deploymentName + "' 中的容器已停止或未找到任何容器配置。");
            }
            return ResponseEntity.ok("部署 '" + deploymentName + "' 已发送停止指令。已停止容器: " + stoppedContainers);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("停止部署失败: 找不到配置或文件错误: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("停止部署 '" + deploymentName + "' 失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("停止部署失败: " + e.getMessage());
        }
    }

    /**
     * 删除单个容器
     * @param serviceName 容器名称 (service_name)
     */
    @DeleteMapping("/remove/{serviceName}")
    public ResponseEntity<String> removeContainer(@PathVariable String serviceName) {
        try {
            dockerService.removeContainer(serviceName);
            return ResponseEntity.ok("容器 '" + serviceName + "' 已删除。");
        } catch (Exception e) {
            // 注意：DockerService.removeContainer 已经处理了 NotFoundException
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("删除容器失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有已上传的部署列表
     * 满足用户需求：list功能列出的是从我上传的那个portal目录下搜索
     */
    @GetMapping("/list")
    public ResponseEntity<List<String>> listDeployments() {
        List<String> deploymentNames = dockerService.listDeployments();
        return ResponseEntity.ok(deploymentNames);
    }

    /**
     * 获取指定部署下所有容器的运行状态。
     * @param deploymentName 部署名称
     */
    @GetMapping("/status/{deploymentName}")
    public ResponseEntity<List<ContainerStatusDTO>> getDeploymentStatus(@PathVariable String deploymentName) {
        try {
            List<ContainerStatusDTO> statusList = dockerService.getDeploymentContainersStatus(deploymentName);
            return ResponseEntity.ok(statusList);
        } catch (IOException e) {
            LOG.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        } catch (Exception e) {
            LOG.error("获取部署状态失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @GetMapping("/getUrlLists/{deploymentName}")
    public ResponseEntity<List<String>> getUrlLists(@PathVariable String deploymentName) {
        try {
            List<String> urlList = dockerService.getUrlLists(deploymentName);
            return ResponseEntity.ok(urlList);
        } catch (IOException e) {
            LOG.error(e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        } catch (Exception e) {
            LOG.error("获取url失败: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }
}