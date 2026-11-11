package com.yohann.ocihelper.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * <p>
 * OciRequestGuard —— 降低 OCI API 调用频率、削平突发流量、被限流时主动退避。
 * </p >
 * <ul>
 *   <li>任务最小间隔 + 随机抖动：避免"每 N 秒整点请求"的机械节奏</li>
 *   <li>429 退避：同一租户被限流后指数级暂停，而不是下一个周期继续硬撞</li>
 *   <li>元数据缓存：可用域/规格/镜像/网络几乎不变，无需每轮重新查询</li>
 *   <li>多账号操作错峰：不再对所有账号同时并发请求</li>
 * </ul>
 */
@Slf4j
public final class OciRequestGuard {

    private OciRequestGuard() {
    }

    /** 开机/换 IP 这类轮询任务的最小间隔（秒），低于该值会被自动抬高 */
    public static final long MIN_TASK_INTERVAL_SECONDS = 60L;
    /** 每轮任务执行前随机延后的最大比例（相对于任务间隔） */
    private static final double TASK_JITTER_RATIO = 0.25D;
    /** 任务首次执行前的随机延迟范围（秒） */
    private static final long INITIAL_DELAY_MIN_SECONDS = 1L;
    private static final long INITIAL_DELAY_MAX_SECONDS = 15L;

    /** 元数据缓存默认有效期：30 分钟 */
    public static final long META_CACHE_TTL_MS = 30 * 60 * 1000L;
    /** 根区间 ID 缓存有效期：24 小时（根区间 OCID 与租户 OCID 相同，不会变化） */
    public static final long ROOT_COMPARTMENT_TTL_MS = 24 * 60 * 60 * 1000L;

    /** 被 429 限流后的首次退避时长：5 分钟，之后翻倍，上限 1 小时 */
    private static final long BACKOFF_BASE_MS = 5 * 60 * 1000L;
    private static final long BACKOFF_MAX_MS = 60 * 60 * 1000L;

    /** 后台多账号操作之间的间隔 */
    private static final long BACKGROUND_GAP_MIN_MS = 2_000L;
    private static final long BACKGROUND_GAP_MAX_MS = 6_000L;
    /** 前台（用户点击触发、同步返回）多账号操作之间的间隔 */
    private static final long INTERACTIVE_GAP_MIN_MS = 400L;
    private static final long INTERACTIVE_GAP_MAX_MS = 1_200L;

    /** 等待资源状态变化时的轮询间隔：4~6 秒 */
    private static final long POLL_MIN_MS = 4_000L;
    private static final long POLL_MAX_MS = 6_000L;

    // ------------------------------------------------------------------ 间隔 / 抖动

    /** 返回不低于最小间隔的任务周期（秒） */
    public static long effectivePeriodSeconds(long periodSeconds) {
        return Math.max(periodSeconds, MIN_TASK_INTERVAL_SECONDS);
    }

    /** 任务首次执行前的随机延迟（秒），避免多个任务在同一秒一起发请求 */
    public static long randomInitialDelaySeconds() {
        return ThreadLocalRandom.current().nextLong(INITIAL_DELAY_MIN_SECONDS, INITIAL_DELAY_MAX_SECONDS + 1);
    }

    /** 每一轮执行前随机延后的毫秒数：[0, 周期 * 25%] */
    public static long taskJitterMillis(long periodSeconds) {
        long max = (long) (periodSeconds * 1000L * TASK_JITTER_RATIO);
        return max <= 0 ? 0 : ThreadLocalRandom.current().nextLong(0, max + 1);
    }

    /** 后台任务多账号操作之间的错峰等待 */
    public static void pauseBetweenAccounts() {
        sleepQuietly(ThreadLocalRandom.current().nextLong(BACKGROUND_GAP_MIN_MS, BACKGROUND_GAP_MAX_MS + 1));
    }

    /** 前台同步操作多账号之间的短暂等待（不至于让页面请求超时） */
    public static void pauseShort() {
        sleepQuietly(ThreadLocalRandom.current().nextLong(INTERACTIVE_GAP_MIN_MS, INTERACTIVE_GAP_MAX_MS + 1));
    }

    /** 轮询资源状态时的等待毫秒数 */
    public static long pollIntervalMillis() {
        return ThreadLocalRandom.current().nextLong(POLL_MIN_MS, POLL_MAX_MS + 1);
    }

    /** 随机等待 [0, maxMillis] 毫秒 */
    public static void sleepRandom(long maxMillis) {
        if (maxMillis <= 0) {
            return;
        }
        sleepQuietly(ThreadLocalRandom.current().nextLong(0, maxMillis + 1));
    }

    public static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ 429 退避

    /** key -> {下次允许请求的时间戳, 连续被限流次数} */
    private static final Map<String, long[]> BACKOFF = new ConcurrentHashMap<>();

    /** 该租户当前是否处于退避期（应跳过本轮请求） */
    public static boolean isBackingOff(String key) {
        if (key == null) {
            return false;
        }
        long[] state = BACKOFF.get(key);
        return state != null && System.currentTimeMillis() < state[0];
    }

    /** 记录一次 429：指数退避 5min -> 10min -> 20min -> ... 上限 1h */
    public static void recordThrottled(String key) {
        if (key == null) {
            return;
        }
        long[] state = BACKOFF.compute(key, (k, old) -> {
            long level = old == null ? 0 : old[1];
            long wait = Math.min(BACKOFF_BASE_MS << Math.min(level, 10), BACKOFF_MAX_MS);
            // 在退避时长上再加最多 20% 的随机量
            wait += ThreadLocalRandom.current().nextLong(0, wait / 5 + 1);
            return new long[]{System.currentTimeMillis() + wait, level + 1};
        });
        log.warn("【请求保护】租户触发 OCI 限流(429)，暂停该租户请求约 {} 分钟（连续第 {} 次）",
                Math.max(1, (state[0] - System.currentTimeMillis()) / 60_000L), state[1]);
    }

    /** 本轮请求没有被限流，清除退避状态 */
    public static void recordOk(String key) {
        if (key != null) {
            BACKOFF.remove(key);
        }
    }

    // ------------------------------------------------------------------ 元数据缓存

    /** key -> {过期时间戳, 值} */
    private static final Map<String, Object[]> META_CACHE = new ConcurrentHashMap<>();

    public static Object getCached(String key) {
        Object[] entry = META_CACHE.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > (Long) entry[0]) {
            META_CACHE.remove(key);
            return null;
        }
        return entry[1];
    }

    public static void putCached(String key, Object value) {
        putCached(key, value, META_CACHE_TTL_MS);
    }

    public static void putCached(String key, Object value, long ttlMillis) {
        if (value != null) {
            META_CACHE.put(key, new Object[]{System.currentTimeMillis() + ttlMillis, value});
        }
    }

    /** 命中缓存则直接返回，否则调用 loader 并缓存（loader 返回 null 不缓存） */
    public static <T> T cached(String key, Supplier<T> loader) {
        return cachedIf(key, META_CACHE_TTL_MS, v -> true, loader);
    }

    /** 只有当 accept 返回 true 时才写入缓存 */
    public static <T> T cachedIf(String key, Predicate<T> accept, Supplier<T> loader) {
        return cachedIf(key, META_CACHE_TTL_MS, accept, loader);
    }

    @SuppressWarnings("unchecked")
    public static <T> T cachedIf(String key, long ttlMillis, Predicate<T> accept, Supplier<T> loader) {
        Object hit = getCached(key);
        if (hit != null) {
            return (T) hit;
        }
        T value = loader.get();
        if (value != null && accept.test(value)) {
            putCached(key, value, ttlMillis);
        }
        return value;
    }

    /** 清除指定前缀的所有缓存（创建实例遇到非容量类错误时调用，下一轮重新读取最新状态） */
    public static void invalidate(String prefix) {
        META_CACHE.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
