package com.seal.portalbackend.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DockerConfig {

    @Value("${docker.client.host_linux}")
    private String dockerHostLinux;

    @Value("${docker.client.host_windows}")
    private String dockerHostWindows;

    @Value("${docker.client.api-version}")
    private String apiVersion;

    @Bean
    public DockerClient dockerClient() {

        String currentDockerHost;
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            currentDockerHost = dockerHostWindows;
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix") || osName.contains("mac")) {
            currentDockerHost = dockerHostLinux;
        } else {
            currentDockerHost = dockerHostLinux;
        }

        if (currentDockerHost == null || currentDockerHost.isEmpty()) {
            throw new IllegalStateException("Docker Host URI 未配置。请检查 application.properties 中 'docker.client.host_windows' 或 'docker.client.host_linux' 的值。");
        }

        DefaultDockerClientConfig config = new DefaultDockerClientConfig.Builder()
                .withDockerHost(currentDockerHost)
                .withApiVersion(apiVersion)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}