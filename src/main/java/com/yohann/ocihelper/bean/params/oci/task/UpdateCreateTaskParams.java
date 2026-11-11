package com.yohann.ocihelper.bean.params.oci.task;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Parameters for updating an existing create-instance task.
 * The service will stop the current task and re-submit it with the new settings.
 */
@Data
public class UpdateCreateTaskParams {

    /** ID of the task to update */
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

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
