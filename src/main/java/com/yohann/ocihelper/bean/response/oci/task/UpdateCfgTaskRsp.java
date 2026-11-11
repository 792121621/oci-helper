package com.yohann.ocihelper.bean.response.oci.task;

import lombok.Data;

/**
 * In-memory update-CPU/memory task response DTO.
 */
@Data
public class UpdateCfgTaskRsp {
    /** Unique task id */
    private String id;
    /** Config display name */
    private String username;
    /** OCI region */
    private String region;
    /** Instance display name */
    private String instanceName;
    /** Instance id */
    private String instanceId;
    /** Target OCPUs */
    private String ocpus;
    /** Target memory (GB) */
    private String memory;
    /** 0 = running, 1 = paused */
    private Integer paused;
    /** Task creation time */
    private String createTime;
    /** Execution count */
    private String counts;
}