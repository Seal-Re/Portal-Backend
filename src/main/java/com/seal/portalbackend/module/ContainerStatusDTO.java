package com.seal.portalbackend.module;

import lombok.Data;

@Data
public class ContainerStatusDTO {

    private String name;        // 容器名称
    private String id;          // 容器 ID 简写
    private String image;       // 镜像名称
    private String status;      // 状态（"Up 35 seconds"）
    private String state;       // 状态（"running"）
    private String hostPort;    // 宿主机端口
    private String containerPort; // 容器内部端口
    private String createdTime; // 创建时间

}
