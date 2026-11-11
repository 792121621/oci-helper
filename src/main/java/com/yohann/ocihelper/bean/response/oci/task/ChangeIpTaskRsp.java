package com.yohann.ocihelper.bean.response.oci.task;

import lombok.Data;

/**
 * In-memory change-IP task response DTO.
 */
@Data
public class ChangeIpTaskRsp {
    /** Unique task id (instanceId) */
    private String id;
    /** Config display name */
    private String username;
    /** OCI region */
    private String region;
    /** Instance display name */
    private String instanceName;
    /** Instance id */
    private String instanceId;
    /** CIDR list (comma-separated, empty means random IP) */
    private String cidrList;
    /** 0 = running, 1 = paused */
    private Integer paused;
    /** Task creation time */
    private String createTime;
    /** Execution count */
    private String counts;
}