package com.freshveggie.controller;

import com.freshveggie.storage.LocalStorageRoot;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源服务 Controller。
 * 用来代替被 Springfox 覆盖掉的 /uploads/**、/doc.html、/webjars/** 等 ResourceHandler。
 */
@Slf4j
@RestController
public class StaticResourceController {

    @Value("${veggie.local-storage.base-path:uploads}")
    private String basePath;

    /** 启动时自动解析出来的真实 uploads 根目录（兼容多种工作目录） */
    private Path uploadsRoot;

    @PostConstruct
    public void resolveUploadsRoot() throws IOException {
        // 与 LocalStorageAspect（写）、LocalStorageWebConfig（映射）共用同一个解析器，
        // 保证「写到哪里就从哪里读」
        uploadsRoot = LocalStorageRoot.resolve(basePath);
        log.info("[StaticResource] uploads 根目录已解析: {}", uploadsRoot);
    }

    /**
     * 服务 /uploads/** 下的本地文件
     */
    @GetMapping("/uploads/**")
    public void serveUploads(HttpServletRequest request,
                             HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        String relative = decode(uri.substring("/uploads/".length()));
        // 防穿越
        if (relative.contains("..") || relative.startsWith("/") || relative.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":0,\"msg\":\"非法路径\",\"data\":null}");
            return;
        }
        Path target = uploadsRoot.resolve(relative).normalize();
        // 二次防穿越：解析后必须仍在 uploadsRoot 内
        if (!target.startsWith(uploadsRoot)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        log.debug("[StaticResource] 请求 {} -> 解析路径: {}", uri, target);
        serveFile(target, response);
    }

    /**
     * Swagger doc.html（classpath 资源）
     */
    @GetMapping("/doc.html")
    public void serveDocHtml(HttpServletResponse response) throws IOException, URISyntaxException {
        serveClasspath("/static/doc.html", response);
    }

    /**
     * Springfox webjars 静态资源
     */
    @GetMapping("/webjars/**")
    public void serveWebjars(HttpServletRequest request,
                             HttpServletResponse response) throws IOException, URISyntaxException {
        String uri = request.getRequestURI();
        String relative = decode(uri.substring("/webjars/".length()));
        if (relative.contains("..") || relative.startsWith("/") || relative.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        serveClasspath("/META-INF/resources/webjars/" + relative, response);
    }

    private void serveFile(Path target, HttpServletResponse response) throws IOException {
        File file = target.toFile();
        if (!file.exists() || !file.isFile()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":0,\"msg\":\"文件不存在: " + target + "\",\"data\":null}");
            return;
        }
        stream(file.toPath(), response);
    }

    private void serveClasspath(String classpath, HttpServletResponse response) throws IOException, URISyntaxException {
        var url = getClass().getResource(classpath);
        if (url == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path p = Paths.get(url.toURI());
        stream(p, response);
    }

    private void stream(Path p, HttpServletResponse response) throws IOException {
        String mime = Files.probeContentType(p);
        if (mime == null) mime = "application/octet-stream";
        response.setContentType(mime);
        response.setContentLengthLong(Files.size(p));
        response.setHeader("Cache-Control", "max-age=86400");
        Files.copy(p, response.getOutputStream());
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}