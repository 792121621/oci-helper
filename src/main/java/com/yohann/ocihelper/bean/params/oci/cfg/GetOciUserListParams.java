package com.yohann.ocihelper.bean.params.oci.cfg;

import lombok.Data;

/**
 * <p>
 * GetOciUserListParams
 * </p >
 *
 * @author yohann
 * @since 2024/11/12 17:24
 */
@Data
public class GetOciUserListParams {

    private String keyword;
    private long currentPage;
    private long pageSize;
    private Integer isEnableCreate;
    /** Filter by plan type: PAYG / FREE_TIER, null means all */
    private String planType;
    /** Filter by account status: ACTIVE / INACTIVE / NONE(null records), null means all */
    private String accountStatus;
    /** Sort order for tenant_create_time: ASC / DESC, default DESC */
    private String sortOrder;
}
