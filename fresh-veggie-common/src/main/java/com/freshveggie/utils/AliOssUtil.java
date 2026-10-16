package com.freshveggie.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.DeleteObjectsRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.properties.AliOssProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AliOssUtil {

    @Autowired(required = false)
    private OSS ossClient;

    @Autowired
    private AliOssProperties aliOssProperties;

    public String upload(MultipartFile file) {

        String originalFileName = file.getOriginalFilename();
        String fileName = new SimpleDateFormat("yyyyMMdd").format(new Date())
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + originalFileName.substring(originalFileName.lastIndexOf("."));

        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentDisposition("inline");
        objectMetadata.setContentType(file.getContentType());

        try {
            // 创建PutObject请求。
            ossClient.putObject(aliOssProperties.getBucketName(), fileName, new ByteArrayInputStream(file.getBytes()), objectMetadata);
        } catch (Exception e) {
            log.error("文件上传失败：", e);
            throw new BusinessException("文件上传失败");
        }
        //文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(aliOssProperties.getBucketName())
                .append(".")
                .append(aliOssProperties.getEndpoint())
                .append("/")
                .append(fileName);

        log.info("文件上传到: {}", stringBuilder);

        return stringBuilder.toString();
    }

    @Async
    public void deleteFile(String filepath) {

        // https://fresh-veggie-oss-cn-shenzhen.aliyuncs.com/20250906/example-product-image.jpg
        StringBuilder sb = new StringBuilder();
        sb.append("https://");
        sb.append(aliOssProperties.getBucketName());
        sb.append(".");
        sb.append(aliOssProperties.getEndpoint());
        sb.append("/");
        try {
            ossClient.deleteObject(aliOssProperties.getBucketName(), filepath.split(sb.toString())[1]);
            log.info("oss文件删除成功，文件路径：{}", filepath);
        } catch (Exception e) {
            log.error("异常信息：", e);
        }
    }

    @Async
    public void deleteFileBatch(List<String> filepath) {
        StringBuilder sb = new StringBuilder();
        sb.append("https://");
        sb.append(aliOssProperties.getBucketName());
        sb.append(".");
        sb.append(aliOssProperties.getEndpoint());
        sb.append("/");
        try {
            List<String> keys = filepath.stream().map(s -> s.split(sb.toString())[1]).collect(Collectors.toList());
            ossClient.deleteObjects(new DeleteObjectsRequest(aliOssProperties.getBucketName()).withKeys(keys).withQuiet(true));
            for (String s : filepath) {
                log.info("oss文件删除成功，地址为：{}", s);
            }
        } catch (Exception e) {
            log.error("异常信息：", e);
        }
    }

    // 在对象销毁前关闭客户端连接
    @PreDestroy
    private void closeOSSClient() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

}
