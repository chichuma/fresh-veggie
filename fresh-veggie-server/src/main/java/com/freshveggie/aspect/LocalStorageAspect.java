package com.freshveggie.aspect;

import com.freshveggie.storage.LocalStorageRoot;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 本地磁盘存储切面：当未配置 veggie.alioss.access-key-id 时，
 * 拦截 AliOssUtil 的上传/删除方法，把文件转存到本地磁盘。
 */
@Aspect
@Component
@Slf4j
public class LocalStorageAspect {

    private final Environment environment;
    private final ServerProperties serverProperties;

    @Value("${veggie.local-storage.base-path:uploads}")
    private String basePath;

    /** 写入用的本地存储根目录，启动时解析一次后固定，与静态资源服务读取的是同一个目录 */
    private Path uploadsRoot;

    private boolean ossEnabled;

    public LocalStorageAspect(Environment environment, ServerProperties serverProperties) {
        this.environment = environment;
        this.serverProperties = serverProperties;
    }

    @PostConstruct
    public void init() throws IOException {
        String keyId = environment.getProperty("veggie.alioss.access-key-id");
        ossEnabled = keyId != null && !keyId.isBlank();
        // 无论 OSS 是否启用都先解析：目录不一致的问题必须在启动日志里就能看出来
        uploadsRoot = LocalStorageRoot.resolve(basePath);
        if (!ossEnabled) {
            log.info("未配置阿里云OSS，文件上传将使用本地磁盘存储，目录: {}", uploadsRoot);
        }
    }

    /**
     * 拦截上传，转存本地并返回本地访问 URL
     */
    @Around("execution(* com.freshveggie.utils.AliOssUtil.upload(..))")
    public Object interceptUpload(ProceedingJoinPoint pjp) throws Throwable {
        if (ossEnabled) {
            return pjp.proceed();
        }
        MultipartFile file = (MultipartFile) pjp.getArgs()[0];
        String originalFileName = file.getOriginalFilename();
        String fileName = new SimpleDateFormat("yyyyMMdd").format(new Date())
                + "/"
                + UUID.randomUUID().toString().replace("-", "")
                + originalFileName.substring(originalFileName.lastIndexOf("."));

        Path target = uploadsRoot.resolve(fileName);
        Files.createDirectories(target.getParent());
        file.transferTo(target.toFile());

        String url = baseUrl() + "/" + fileName.replace("\\", "/");
        log.info("文件已保存到本地: {}", target.toAbsolutePath());
        return url;
    }

    /**
     * 拦截单文件删除
     */
    @Around("execution(* com.freshveggie.utils.AliOssUtil.deleteFile(..)) && args(filepath)")
    public Object interceptDeleteFile(ProceedingJoinPoint pjp, String filepath) throws Throwable {
        if (ossEnabled) {
            return pjp.proceed();
        }
        deleteLocal(filepath);
        return null;
    }

    /**
     * 拦截批量删除
     */
    @Around("execution(* com.freshveggie.utils.AliOssUtil.deleteFileBatch(..)) && args(filepaths)")
    public Object interceptDeleteFileBatch(ProceedingJoinPoint pjp, List<String> filepaths) throws Throwable {
        if (ossEnabled) {
            return pjp.proceed();
        }
        for (String filepath : filepaths) {
            deleteLocal(filepath);
        }
        return null;
    }

    private void deleteLocal(String url) {
        try {
            int index = url.indexOf(LocalStorageRoot.URL_PREFIX);
            if (index < 0) {
                log.warn("无法解析本地文件路径: {}", url);
                return;
            }
            String relative = url.substring(index + LocalStorageRoot.URL_PREFIX.length());
            Path target = uploadsRoot.resolve(relative).normalize();
            // 防穿越：解析后必须仍在存储根目录内，避免 URL 里的 .. 删到目录外的文件
            if (!target.startsWith(uploadsRoot)) {
                log.warn("拒绝删除存储根目录之外的路径: {} -> {}", url, target);
                return;
            }
            File file = target.toFile();
            if (file.exists() && file.delete()) {
                log.info("本地文件删除成功: {}", file.getAbsolutePath());
            } else {
                log.warn("本地文件删除失败或不存在: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.error("本地文件删除异常: {}", url, e);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + serverProperties.getPort() + LocalStorageRoot.URL_PATH;
    }
}
