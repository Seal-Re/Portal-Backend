package com.seal.portalbackend.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface UploaderService {

    ResponseEntity<String> uploadDeploymentFiles(MultipartFile tarFile, MultipartFile configFile, String name);

    ResponseEntity<String> deleteDeploymentFiles(String name);
}
