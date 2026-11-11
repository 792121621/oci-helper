package com.yohann.ocihelper.bean.params.oci.instance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @ClassName Close500MParams
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-08-25 10:08
 **/
@Data
public class Close500MParams {

    @NotBlank(message = "ociCfgId不能为空")
    private String ociCfgId;
    @NotBlank(message = "instanceId不能为空")
    private String instanceId;
    @NotNull(message = "是否保留网络负载平衡器不能为空")
    private Boolean retainBl;
    @NotNull(message = "是否保留NAT网关不能为空")
    private Boolean retainNatGw;
}
