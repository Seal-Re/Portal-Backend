package com.seal.portalbackend.service;

import com.github.dockerjava.api.model.*;
import com.seal.portalbackend.ett.PortalConfig;
import com.seal.portalbackend.module.ContainerStatusDTO;

import java.io.*;
import java.util.List;
import java.util.Map;

public interface DockerService {

    PortalConfig readConfig(String deploymentName) throws IOException;

    void loadImage(String deploymentName, String tarFileName) throws IOException;

    List<String> startDeployment(String deploymentName) throws IOException;

    List<String> stopDeployment(String deploymentName) throws IOException;

    void removeContainer(String containerNameOrId);

    List<ContainerStatusDTO> getDeploymentContainersStatus(String deploymentName) throws IOException;

    List<String> listDeployments();

    void stopAndRemove(String deploymentName);

    void renameImage(String oldImageName, String newImageName) throws IOException;

    void loadAndRetagImages(String deploymentName, String tarFileName, Map<String, String> imageRenamingMap) throws IOException;

    List<String> getUrlLists(String deploymentName) throws IOException;
}