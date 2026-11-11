package com.yohann.ocihelper.bean.params.oci.securityrule;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * <p>
 * ReleaseSecurityRuleByVcnParams - release all ports for a specific VCN
 * </p>
 *
 * @author yohann
 */
@Data
public class ReleaseSecurityRuleByVcnParams {

    @NotBlank(message = "配置id不能为空")
    private String ociCfgId;

    @NotBlank(message = "VCN id不能为空")
    private String vcnId;
}
