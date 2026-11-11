package com.yohann.ocihelper.enums;

import lombok.Getter;

/**
 * Account status enum for OCI user alive check results.
 * OCI 用户测活账户状态枚举
 *
 * @author yohann
 */
@Getter
public enum AccountStatusEnum {

    ACTIVE("ACTIVE", "活跃"),
    INACTIVE("INACTIVE", "失效"),
    ;

    /** Value stored in DB column account_status */
    private final String code;
    /** Human-readable description */
    private final String desc;

    AccountStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
