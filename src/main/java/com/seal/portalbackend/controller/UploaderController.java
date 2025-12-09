package com.seal.portalbackend.controller;

import com.seal.portalbackend.service.UploaderService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
/**
 * 负责接收部署文件 (tar包和config.yaml) 并存储到服务器指定目录。
 * 存储路径：BASE_DIR (即 /root/portal/) 下的子目录 {name}。
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploaderController {

    @Autowired
    private UploaderService uploaderService ;

    private final Logger LOG = LoggerFactory.getLogger(this.getClass());

    /**
     * 接收 Docker tar 文件、config.yaml 和部署名称，并存储到指定目录。
     * @param tarFile 镜像 tar 包
     * @param configFile 部署配置文件 (config.yaml)
     * @param name 部署/应用名称 (用于创建目录)
     */
    @PostMapping("/deployment")
    public ResponseEntity<String> uploadDeploymentFiles(
            @RequestParam("tarFile") MultipartFile tarFile,
            @RequestParam("configFile") MultipartFile configFile,
            @RequestParam("name") String name) {
        return uploaderService.uploadDeploymentFiles(tarFile, configFile, name);
    }

    /**
     * 接收部署名称，删除指定目录
     * @param name 部署名称
     */
    @PostMapping("/delete/{name}")
    public ResponseEntity<String> deleteDeploymentFiles(@PathVariable("name") String name ) {
        return uploaderService.deleteDeploymentFiles(name);
    }

}