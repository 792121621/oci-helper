package com.yohann.ocihelper.controller;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.IdListParams;
import com.yohann.ocihelper.bean.params.oci.instance.ChangeIpParams;
import com.yohann.ocihelper.bean.params.oci.instance.UpdateInstanceCfgParams;
import com.yohann.ocihelper.bean.response.oci.task.ChangeIpTaskRsp;
import com.yohann.ocihelper.bean.response.oci.task.UpdateCfgTaskRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.IInstanceService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.service.impl.OciServiceImpl;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;
import static com.yohann.ocihelper.service.impl.OciServiceImpl.addTask;
import static com.yohann.ocihelper.service.impl.OciServiceImpl.stopTask;

/**
 * <p>
 * InMemoryTaskController - manages in-memory change-IP tasks and
 * update-CPU/memory tasks (every 60 s, not persisted to DB).
 * </p>
 *
 * @author yohann
 */
@Slf4j
@RestController
@RequestMapping("/api/inMemoryTask")
public class InMemoryTaskController {

    // -------------------------------------------------------------------------
    // Task prefix constants (must differ from CREATE_TASK_PREFIX)
    // -------------------------------------------------------------------------
    public static final String CHANGE_IP_TASK_KEY = "MEM_CHANGE_IP_";
    public static final String UPDATE_CFG_TASK_KEY = "MEM_UPDATE_CFG_";
    public static final String CHANGE_IP_CNT_KEY = "MEM_CHANGE_IP_CNT_";
    public static final String UPDATE_CFG_CNT_KEY = "MEM_UPDATE_CFG_CNT_";

    /**
     * In-memory metadata store for change-IP tasks
     */
    public static final Map<String, ChangeIpTaskMeta> CHANGE_IP_TASKS = new ConcurrentHashMap<>();
    /**
     * In-memory metadata store for update-cfg tasks
     */
    public static final Map<String, UpdateCfgTaskMeta> UPDATE_CFG_TASKS = new ConcurrentHashMap<>();

    @Resource
    private ISysService sysService;
    @Resource
    private IInstanceService instanceService;
    @Resource
    private OciServiceImpl ociService;

    // =========================================================================
    // ======================== Change-IP Task APIs ==========================
    // =========================================================================

    /**
     * Add a change-IP repeating task (every 60 s)
     */
    @PostMapping("/changeIp/add")
    public ResponseData<Void> addChangeIpTask(@Validated @RequestBody ChangeIpParams params) {
        String taskId = IdUtil.fastSimpleUUID();
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());

        // Resolve instance name once for display
        String instanceName = params.getInstanceId();

        ChangeIpTaskMeta meta = new ChangeIpTaskMeta();
        meta.setId(taskId);
        meta.setUsername(sysUserDTO.getUsername());
        meta.setRegion(sysUserDTO.getOciCfg().getRegion());
        meta.setInstanceName(instanceName);
        meta.setInstanceId(params.getInstanceId());
        meta.setCidrList(params.getCidrList() == null ? Collections.emptyList() : params.getCidrList());
        meta.setPaused(0);
        meta.setCreateTime(LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM));
        meta.setParams(params);
        CHANGE_IP_TASKS.put(taskId, meta);
        TEMP_MAP.put(CHANGE_IP_CNT_KEY + taskId, 0L);

        // Schedule the task every 60 s with initial delay 0
        addTask(CHANGE_IP_TASK_KEY + taskId, () -> {
            ChangeIpTaskMeta m = CHANGE_IP_TASKS.get(taskId);
            if (m == null || m.getPaused() == 1)
                return;
            // Increment attempt counter before each execution
            TEMP_MAP.merge(CHANGE_IP_CNT_KEY + taskId, 1L,
                    (a, b) -> ((Number) a).longValue() + ((Number) b).longValue());
            try {
                SysUserDTO dto = sysService.getOciUser(params.getOciCfgId());
                ociService.execChange(params, dto, instanceService, 60, () -> {
                    // On success: stop this in-memory task and clean up metadata
                    stopTask(CHANGE_IP_TASK_KEY + taskId);
                    CHANGE_IP_TASKS.remove(taskId);
                    TEMP_MAP.remove(CHANGE_IP_CNT_KEY + taskId);
                    log.info("[ChangeIpTask] taskId={} IP changed successfully, task removed", taskId);
                });
            } catch (Exception e) {
                log.error("[ChangeIpTask] taskId={} error: {}", taskId, e.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS);

        log.info("[ChangeIpTask] added taskId={} instance={}", taskId, params.getInstanceId());
        return ResponseData.successData("换IP任务已添加，每60秒执行一次");
    }

    /**
     * Query change-IP task list with keyword fuzzy search + pagination
     */
    @PostMapping("/changeIp/page")
    public ResponseData<Page<ChangeIpTaskRsp>> changeIpPage(@RequestBody TaskPageParams params) {
        String kw = params.getKeyword() == null ? "" : params.getKeyword().toLowerCase();
        List<ChangeIpTaskRsp> all = CHANGE_IP_TASKS.values().stream()
                .filter(m -> kw.isEmpty()
                        || m.getUsername().toLowerCase().contains(kw)
                        || m.getRegion().toLowerCase().contains(kw)
                        || m.getInstanceName().toLowerCase().contains(kw))
                .sorted(Comparator.comparing(ChangeIpTaskMeta::getCreateTime).reversed())
                .map(m -> {
                    ChangeIpTaskRsp r = new ChangeIpTaskRsp();
                    r.setId(m.getId());
                    r.setUsername(m.getUsername());
                    r.setRegion(m.getRegion());
                    r.setInstanceName(m.getInstanceName());
                    r.setInstanceId(m.getInstanceId());
                    r.setCidrList(m.getCidrList().isEmpty() ? "随机IP" : String.join(", ", m.getCidrList()));
                    r.setPaused(m.getPaused());
                    r.setCreateTime(m.getCreateTime());
                    Object cnt = TEMP_MAP.get(CHANGE_IP_CNT_KEY + m.getId());
                    r.setCounts(cnt == null ? "0" : String.valueOf(cnt));
                    return r;
                })
                .collect(Collectors.toList());

        return ResponseData.successData(buildPage(all, params), "查询成功");
    }

    /**
     * Pause change-IP tasks
     */
    @PostMapping("/changeIp/pause")
    public ResponseData<Void> pauseChangeIp(@Validated @RequestBody IdListParams params) {
        params.getIdList().forEach(id -> {
            stopTask(CHANGE_IP_TASK_KEY + id);
            ChangeIpTaskMeta m = CHANGE_IP_TASKS.get(id);
            if (m != null)
                m.setPaused(1);
        });
        return ResponseData.successData("已暂停");
    }

    /**
     * Resume change-IP tasks
     */
    @PostMapping("/changeIp/resume")
    public ResponseData<Void> resumeChangeIp(@Validated @RequestBody IdListParams params) {
        params.getIdList().forEach(id -> {
            ChangeIpTaskMeta m = CHANGE_IP_TASKS.get(id);
            if (m == null)
                return;
            m.setPaused(0);
            addTask(CHANGE_IP_TASK_KEY + id, () -> {
                ChangeIpTaskMeta cur = CHANGE_IP_TASKS.get(id);
                if (cur == null || cur.getPaused() == 1)
                    return;
                // Increment attempt counter before each execution
                TEMP_MAP.merge(CHANGE_IP_CNT_KEY + id, 1L,
                        (a, b) -> ((Number) a).longValue() + ((Number) b).longValue());
                try {
                    SysUserDTO dto = sysService.getOciUser(cur.getParams().getOciCfgId());
                    ociService.execChange(cur.getParams(), dto, instanceService, 60, () -> {
                        // On success: stop this in-memory task and clean up metadata
                        stopTask(CHANGE_IP_TASK_KEY + id);
                        CHANGE_IP_TASKS.remove(id);
                        TEMP_MAP.remove(CHANGE_IP_CNT_KEY + id);
                        log.info("[ChangeIpTask] resume taskId={} IP changed successfully, task removed", id);
                    });
                } catch (Exception e) {
                    log.error("[ChangeIpTask] resume taskId={} error: {}", id, e.getMessage());
                }
            }, 0, 60, TimeUnit.SECONDS);
        });
        return ResponseData.successData("已恢复");
    }

    /**
     * Delete (stop + remove) change-IP tasks
     */
    @PostMapping("/changeIp/delete")
    public ResponseData<Void> deleteChangeIp(@Validated @RequestBody IdListParams params) {
        params.getIdList().forEach(id -> {
            stopTask(CHANGE_IP_TASK_KEY + id);
            CHANGE_IP_TASKS.remove(id);
            TEMP_MAP.remove(CHANGE_IP_CNT_KEY + id);
        });
        return ResponseData.successData("已删除");
    }

    // =========================================================================
    // ===================== Update-CPU/Memory Task APIs =====================
    // =========================================================================

    /**
     * Add a CPU/memory update repeating task (every 60 s)
     */
    @PostMapping("/updateCfg/add")
    public ResponseData<Void> addUpdateCfgTask(@Validated @RequestBody UpdateInstanceCfgParams params) {
        String taskId = IdUtil.fastSimpleUUID();
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());

        // 提前获取实例真实名称，用于后续消息通知
        String instanceName = params.getInstanceId();
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            instanceName = fetcher.getInstanceById(params.getInstanceId()).getDisplayName();
        } catch (Exception e) {
            log.warn("[UpdateCfgTask] 获取实例名称失败，将使用 instanceId 代替: {}", e.getMessage());
        }

        UpdateCfgTaskMeta meta = new UpdateCfgTaskMeta();
        meta.setId(taskId);
        meta.setUsername(sysUserDTO.getUsername());
        meta.setRegion(sysUserDTO.getOciCfg().getRegion());
        meta.setInstanceName(instanceName);
        meta.setInstanceId(params.getInstanceId());
        meta.setOcpus(params.getOcpus());
        meta.setMemory(params.getMemory());
        meta.setPaused(0);
        meta.setCreateTime(LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM));
        meta.setParams(params);
        UPDATE_CFG_TASKS.put(taskId, meta);
        TEMP_MAP.put(UPDATE_CFG_CNT_KEY + taskId, 0L);

        addTask(UPDATE_CFG_TASK_KEY + taskId, () -> {
            UpdateCfgTaskMeta m = UPDATE_CFG_TASKS.get(taskId);
            if (m == null || m.getPaused() == 1)
                return;
            // 每次执行先累加尝试次数
            long cnt = ((Number) TEMP_MAP.merge(UPDATE_CFG_CNT_KEY + taskId, 1L,
                    (a, b) -> ((Number) a).longValue() + ((Number) b).longValue())).longValue();
            try {
                SysUserDTO dto = sysService.getOciUser(params.getOciCfgId());
                instanceService.updateInstanceCfg(dto, params.getInstanceId(),
                        Float.parseFloat(params.getOcpus()), Float.parseFloat(params.getMemory()));
                // 修改成功：发送 TG 通知、停止任务、清除元数据
                UpdateCfgTaskMeta successMeta = UPDATE_CFG_TASKS.get(taskId);
                String instName = successMeta != null ? successMeta.getInstanceName() : params.getInstanceId();
                log.info("[UpdateCfgTask] taskId={} 第{}次执行，修改配置成功，自动停止任务 ocpus={} memory={}",
                        taskId, cnt, params.getOcpus(), params.getMemory());
                String msg = String.format(CommonUtils.UPDATE_CFG_MESSAGE_TEMPLATE,
                        dto.getUsername(),
                        LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                        dto.getOciCfg().getRegion(),
                        instName,
                        params.getOcpus(), params.getMemory());
                sysService.sendMessage(msg);
                stopTask(UPDATE_CFG_TASK_KEY + taskId);
                UPDATE_CFG_TASKS.remove(taskId);
                TEMP_MAP.remove(UPDATE_CFG_CNT_KEY + taskId);
            } catch (Exception e) {
                // 修改失败：记录日志，等待下一轮（60s后）重试
                log.warn("[UpdateCfgTask] taskId={} 第{}次执行，修改配置失败，60s后重试，原因: {}", taskId, cnt, e.getMessage());
            }
        }, 0, 60, TimeUnit.SECONDS);

        log.info("[UpdateCfgTask] added taskId={} instance={}", taskId, params.getInstanceId());
        return ResponseData.successData("修改配置任务已添加，每60秒执行一次");
    }

    /**
     * Query update-cfg task list with keyword fuzzy search + pagination
     */
    @PostMapping("/updateCfg/page")
    public ResponseData<Page<UpdateCfgTaskRsp>> updateCfgPage(@RequestBody TaskPageParams params) {
        String kw = params.getKeyword() == null ? "" : params.getKeyword().toLowerCase();
        List<UpdateCfgTaskRsp> all = UPDATE_CFG_TASKS.values().stream()
                .filter(m -> kw.isEmpty()
                        || m.getUsername().toLowerCase().contains(kw)
                        || m.getRegion().toLowerCase().contains(kw)
                        || m.getInstanceName().toLowerCase().contains(kw))
                .sorted(Comparator.comparing(UpdateCfgTaskMeta::getCreateTime).reversed())
                .map(m -> {
                    UpdateCfgTaskRsp r = new UpdateCfgTaskRsp();
                    r.setId(m.getId());
                    r.setUsername(m.getUsername());
                    r.setRegion(m.getRegion());
                    r.setInstanceName(m.getInstanceName());
                    r.setInstanceId(m.getInstanceId());
                    r.setOcpus(m.getOcpus());
                    r.setMemory(m.getMemory());
                    r.setPaused(m.getPaused());
                    r.setCreateTime(m.getCreateTime());
                    Object cnt = TEMP_MAP.get(UPDATE_CFG_CNT_KEY + m.getId());
                    r.setCounts(cnt == null ? "0" : String.valueOf(cnt));
                    return r;
                })
                .collect(Collectors.toList());

        return ResponseData.successData(buildPage(all, params), "查询成功");
    }

    /**
     * Pause update-cfg tasks
     */
    @PostMapping("/updateCfg/pause")
    public ResponseData<Void> pauseUpdateCfg(@Validated @RequestBody IdListParams params) {
        params.getIdList().forEach(id -> {
            stopTask(UPDATE_CFG_TASK_KEY + id);
            UpdateCfgTaskMeta m = UPDATE_CFG_TASKS.get(id);
            if (m != null)
                m.setPaused(1);
        });
        return ResponseData.successData("已暂停");
    }

    /**
     * Resume update-cfg tasks
     */
    @PostMapping("/updateCfg/resume")
    public ResponseData<Void> resumeUpdateCfg(@Validated @RequestBody IdListParams params) {
        params.getIdList().forEach(id -> {
            UpdateCfgTaskMeta m = UPDATE_CFG_TASKS.get(id);
            if (m == null)
                return;
            m.setPaused(0);
            addTask(UPDATE_CFG_TASK_KEY + id, () -> {
                UpdateCfgTaskMeta cur = UPDATE_CFG_TASKS.get(id);
                if (cur == null || cur.getPaused() == 1)
                    return;
                // 每次执行先累加尝试次数
                long cnt = ((Number) TEMP_MAP.merge(UPDATE_CFG_CNT_KEY + id, 1L,
                        (a, b) -> ((Number) a).longValue() + ((Number) b).longValue())).longValue();
                try {
                    SysUserDTO dto = sysService.getOciUser(cur.getParams().getOciCfgId());
                    instanceService.updateInstanceCfg(dto, cur.getParams().getInstanceId(),
                            Float.parseFloat(cur.getParams().getOcpus()), Float.parseFloat(cur.getParams().getMemory()));
                    // 修改成功：发送 TG 通知、停止任务、清除元数据
                    log.info("[UpdateCfgTask] taskId={} 第{}次执行，修改配置成功，自动停止任务", id, cnt);
                    String msgR = String.format(CommonUtils.UPDATE_CFG_MESSAGE_TEMPLATE,
                            dto.getUsername(),
                            LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern(DatePattern.NORM_DATETIME_PATTERN)),
                            dto.getOciCfg().getRegion(),
                            cur.getInstanceName(),
                            cur.getOcpus(), cur.getMemory());
                    sysService.sendMessage(msgR);
                    stopTask(UPDATE_CFG_TASK_KEY + id);
                    UPDATE_CFG_TASKS.remove(id);
                    TEMP_MAP.remove(UPDATE_CFG_CNT_KEY + id);
                } catch (Exception e) {
                    // 修改失败：记录日志，等待下一轮（60s后）重试
                    log.warn("[UpdateCfgTask] taskId={} 第{}次执行，修改配置失败，60s后重试，原因: {}", id, cnt, e.getMessage());
                }
            }, 0, 60, TimeUnit.SECONDS);
        });
        return ResponseData.successData("已恢复");
    }

    /**
     * Delete (stop + remove) update-cfg tasks
     */
    @PostMapping("/updateCfg/delete")
    public ResponseData<Void> deleteUpdateCfg(@Validated @RequestBody IdListParams params) {
        params.getIdList().forEach(id -> {
            stopTask(UPDATE_CFG_TASK_KEY + id);
            UPDATE_CFG_TASKS.remove(id);
            TEMP_MAP.remove(UPDATE_CFG_CNT_KEY + id);
        });
        return ResponseData.successData("已删除");
    }

    // =========================================================================
    // ============================== Helpers ================================
    // =========================================================================

    private <T> Page<T> buildPage(List<T> all, TaskPageParams params) {
        int page = params.getCurrentPage() < 1 ? 1 : params.getCurrentPage();
        int size = params.getPageSize() < 1 ? 10 : params.getPageSize();
        int total = all.size();
        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        List<T> records = from >= total ? Collections.emptyList() : all.subList(from, to);
        Page<T> p = new Page<>(page, size, total);
        p.setRecords(records);
        return p;
    }

    // =========================================================================
    // ========================== Inner Meta Classes =========================
    // =========================================================================

    @Data
    public static class ChangeIpTaskMeta {
        private String id;
        private String username;
        private String region;
        private String instanceName;
        private String instanceId;
        private List<String> cidrList;
        private Integer paused;
        private String createTime;
        /**
         * Original request params, kept for re-scheduling on resume
         */
        private ChangeIpParams params;
    }

    @Data
    public static class UpdateCfgTaskMeta {
        private String id;
        private String username;
        private String region;
        private String instanceName;
        private String instanceId;
        private String ocpus;
        private String memory;
        private Integer paused;
        private String createTime;
        /**
         * Original request params, kept for re-scheduling on resume
         */
        private UpdateInstanceCfgParams params;
    }

    /**
     * Generic pagination params
     */
    @Data
    public static class TaskPageParams {
        private String keyword;
        private int currentPage = 1;
        private int pageSize = 10;
    }
}
