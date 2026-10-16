package com.freshveggie.config;

import com.freshveggie.storage.LocalStorageRoot;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 将 /uploads/** 映射到本地磁盘目录，配合 LocalStorageAspect 使用。
 *
 * <p>目录不再自己推导，统一走 {@link LocalStorageRoot}，与上传写入、静态读取三处保持一致。
 */
@Slf4j
@Configuration
public class LocalStorageWebConfig implements WebMvcConfigurer {

    @Value("${veggie.local-storage.base-path:uploads}")
    private String basePath;

    private Path uploadsRoot;

    @PostConstruct
    public void init() throws IOException {
        uploadsRoot = LocalStorageRoot.resolve(basePath);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 用 toUri() 拼 file: 前缀，避免手拼字符串在含空格/中文的路径下失效
        String location = uploadsRoot.toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
        log.info("[LocalStorage] 静态资源映射 {} -> {}", "/uploads/**", location);
    }
}
