package com.yohann.ocihelper.bean.params.oci.task;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Parameters for batch-updating existing create-instance tasks.
 * The service will stop each current task and re-submit it with the new settings,
 * staggering restarts by 5 seconds each to avoid API bursts.
 */
@Data
public class UpdateCreateTaskBatchParams {

    /** IDs of the tasks to update */
    @NotEmpty(message = "任务ID列表不能为空")
    private List<String> taskIds;

    /** CPU cores */
    @NotBlank(message = "CPU不能为空")
    private String ocpus;

    /** Memory in GB */
    @NotBlank(message = "内存不能为空")
    private String memory;

    /** Boot-volume size in GB */
    @NotNull(message = "磁盘空间不能为空")
    private Integer disk;

    /** Instance shape / architecture */
    @NotBlank(message = "系统架构不能为空")
    private String architecture;

    /** Polling interval in seconds */
    @NotNull(message = "时间间隔不能为空")
    private Integer interval;

    /** Number of instances to create */
    @NotNull(message = "创建数目不能为空")
    private Integer createNumbers;

    /** OS image keyword */
    @NotBlank(message = "系统类型不能为空")
    private String operationSystem;

    /** root password set via freeform tag */
    private String rootPassword;
}
