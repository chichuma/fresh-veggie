package com.freshveggie.storage;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地文件存储根目录的唯一解析入口。
 *
 * <p>背景：原先 {@code LocalStorageAspect}（写）、{@code LocalStorageWebConfig}（资源映射）
 * 和 {@code StaticResourceController}（读）各自用 {@code Paths.get(basePath).toAbsolutePath()}
 * 独立推导目录，而 {@code user.dir} 既可能是仓库根也可能是 fresh-veggie-server 模块目录，
 * 于是出现「上传写到 &lt;仓库根&gt;/uploads、静态服务却从 &lt;仓库根&gt;/fresh-veggie-server/uploads 读」
 * 的分裂，表现为上传接口返回 200 但图片 404。
 *
 * <p>这里固化两条规则，保证三处永远指向同一个目录：
 * <ol>
 *   <li><b>内容优先</b>：已有的图片目录（如 avatar/category/dish/setmeal）必须被找到；</li>
 *   <li><b>兜底固定</b>：全都没内容时，统一落在 fresh-veggie-server/uploads，
 *       绝不落在系统临时目录或模块外的零散位置。</li>
 * </ol>
 */
@Slf4j
public final class LocalStorageRoot {

    /** 对外 URL 路径（无尾斜杠），与 {@link #URL_PREFIX} 永远只差一个尾斜杠。 */
    public static final String URL_PATH = "/uploads";

    /** 对外 URL 前缀。上传返回的 URL 与删除时反解路径共用，避免两处各写一遍字面量。 */
    public static final String URL_PREFIX = URL_PATH + "/";

    /** basePath 缺省值。 */
    private static final String DEFAULT_BASE_PATH = "uploads";

    /** 兜底目录所在的模块名（多模块仓库里的服务端模块）。 */
    private static final String SERVER_MODULE = "fresh-veggie-server";

    private LocalStorageRoot() {
    }

    /**
     * 解析并确保本地存储根目录存在。
     *
     * @param configuredBasePath 配置项 {@code veggie.local-storage.base-path}，可为空
     * @return 规范化的绝对目录路径（已创建）
     * @throws IOException 目录无法创建时抛出，让启动阶段直接暴露问题而不是上传时才报错
     */
    public static Path resolve(String configuredBasePath) throws IOException {
        return resolve(configuredBasePath, System.getProperty("user.dir", ""));
    }

    /**
     * 可注入工作目录的重载，便于验证多种启动方式下的解析结果。
     *
     * @param configuredBasePath 配置项 {@code veggie.local-storage.base-path}，可为空
     * @param userDir            当前工作目录，传空则按仓库根目录处理
     */
    public static Path resolve(String configuredBasePath, String userDir) throws IOException {
        String base = (configuredBasePath == null || configuredBasePath.isBlank())
                ? DEFAULT_BASE_PATH : configuredBasePath.trim();
        boolean hasUserDir = userDir != null && !userDir.isBlank();

        // 候选顺序即修复本身，不要随意调整：
        //   ① <user.dir>/fresh-veggie-server/uploads —— 仓库根启动时的真实位置（正常开发场景）
        //   ② <user.dir>/uploads —— 以模块目录启动时，它就等于 ①，靠去重避免重复
        //   ③ <user.dir>/../uploads —— 历史兼容
        // 注意 ① 必须排在 ②/③ 之前：否则一旦模块目录下也被建出同名空目录，
        // 空目录会抢走解析结果，又回到「写一个地方、读另一个地方」。
        List<Path> candidates = new ArrayList<>(3);
        if (hasUserDir) {
            addIfAbsent(candidates, Paths.get(userDir, SERVER_MODULE, base));
            addIfAbsent(candidates, Paths.get(userDir, base));
            addIfAbsent(candidates, Paths.get(userDir).resolve("..").resolve(base));
        }
        // ④ 最后兜底：相对当前工作目录
        addIfAbsent(candidates, Paths.get(base));

        if (log.isDebugEnabled()) {
            log.debug("[LocalStorage] 候选目录: {}", candidates);
        }
        // 一档：已有内容的目录必须被找到，否则会把历史图片「孤立」在新的空目录里
        for (Path c : candidates) {
            Path abs = normalize(c);
            if (hasContent(abs)) {
                return use(abs, configuredBasePath);
            }
        }
        // 二档：处处为空，只认已经存在的候选，避免把文件散到模块外的空目录
        for (Path c : candidates) {
            Path abs = normalize(c);
            if (Files.isDirectory(abs)) {
                return use(abs, configuredBasePath);
            }
        }
        // 三档：都不存在，固定落到 <user.dir>/fresh-veggie-server/uploads
        Path fallback = normalize(hasUserDir ? Paths.get(userDir, SERVER_MODULE, base) : Paths.get(base));
        Files.createDirectories(fallback);
        log.warn("[LocalStorage] 未找到任何已存在的 uploads 目录，已创建统一目录: {}", fallback);
        return fallback;
    }

    /** 按绝对路径去重（同一目录的不同写法只保留最先出现的那条候选） */
    private static void addIfAbsent(List<Path> candidates, Path candidate) {
        Path abs = normalize(candidate);
        for (Path existing : candidates) {
            if (normalize(existing).equals(abs)) {
                return;
            }
        }
        candidates.add(candidate);
    }

    private static Path normalize(Path p) {
        return p.toAbsolutePath().normalize();
    }

    private static Path use(Path dir, String configuredBasePath) throws IOException {
        Files.createDirectories(dir);
        log.info("[LocalStorage] 本地存储根目录: {} (veggie.local-storage.base-path={})",
                dir, configuredBasePath);
        return dir;
    }

    /** 目录存在且非空。空目录不算命中，否则历史上被误建的空目录会一直「抢走」解析结果。 */
    private static boolean hasContent(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isPresent();
        } catch (IOException e) {
            log.warn("[LocalStorage] 目录无法读取，跳过: {}", dir, e);
            return false;
        }
    }
}
