package com.seal.portalbackend.utils;

import com.alibaba.fastjson.JSON;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.LoadImageCmd;
import com.github.dockerjava.api.command.TagImageCmd;
import com.github.dockerjava.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.List;

public class DockerUtils {

    private final static Logger logger = LoggerFactory.getLogger(DockerUtils.class);

    /**
     * 获取docker基础信息
     * @param dockerClient
     * @return
     */
    public static String getDockerInfo(DockerClient dockerClient){
        Info info = dockerClient.infoCmd().exec();
        return JSON.toJSONString(info);
    }

    /**
     * 给镜像打标签
     * @param dockerClient
     * @param imageIdOrFullName
     * @param respository
     * @param tag
     */
    public static void tagImage(DockerClient dockerClient, String imageIdOrFullName, String respository,String tag){
        TagImageCmd tagImageCmd = dockerClient.tagImageCmd(imageIdOrFullName, respository, tag);
        tagImageCmd.exec();
    }

    /**
     * load镜像
     * @param dockerClient
     * @param inputStream
     */
    public static void loadImage(DockerClient dockerClient, InputStream inputStream){
        LoadImageCmd loadImageCmd = dockerClient.loadImageCmd(inputStream);
        loadImageCmd.exec();
    }

    /**
     * 推送镜像
     * @param dockerClient
     * @param imageName
     * @return
     * @throws InterruptedException
     */
    public static Boolean pushImage(DockerClient dockerClient,String imageName) throws InterruptedException {
        final Boolean[] result = {true};
        ResultCallback.Adapter<PushResponseItem> callBack = new ResultCallback.Adapter<PushResponseItem>() {
            @Override
            public void onNext(PushResponseItem pushResponseItem) {
                if (pushResponseItem != null){
                    ResponseItem.ErrorDetail errorDetail = pushResponseItem.getErrorDetail();
                    if (errorDetail!= null){
                        result[0] = false;
                        logger.error(errorDetail.getMessage(),errorDetail);
                    }
                }
                super.onNext(pushResponseItem);
            }
        };
        dockerClient.pushImageCmd(imageName).exec(callBack).awaitCompletion();
        return result[0];
    }


    /**
     * 通过dockerFile构建镜像
     * @param dockerClient
     * @param dockerFile
     * @return
     */
    public static String buildImage(DockerClient dockerClient, File dockerFile){
        BuildImageCmd buildImageCmd = dockerClient.buildImageCmd(dockerFile);
        BuildImageResultCallback buildImageResultCallback = new BuildImageResultCallback() {
            @Override
            public void onNext(BuildResponseItem item) {
                logger.info("{}", item);
                super.onNext(item);
            }
        };

        return buildImageCmd.exec(buildImageResultCallback).awaitImageId();
//        logger.info(imageId);
    }

    /**
     * 获取镜像列表
     * @param dockerClient
     * @return
     */
    public static List<Image> imageList(DockerClient dockerClient){
        List<Image> imageList = dockerClient.listImagesCmd().withShowAll(true).exec();
        return imageList;
    }
}