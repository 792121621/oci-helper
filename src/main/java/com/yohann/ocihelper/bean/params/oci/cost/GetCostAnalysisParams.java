package com.yohann.ocihelper.bean.params.oci.cost;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Parameters for querying OCI Cost Analysis (Usage API).
 *
 * @author Yohann
 */
@Data
public class GetCostAnalysisParams {

    /** OCI config ID */
    @NotBlank(message = "配置ID不能为空")
    private String ociCfgId;

    /**
     * Report type, matching OCI built-in report names:
     * COST_BY_SERVICE / COST_BY_SERVICE_AND_DESCRIPTION /
     * COST_BY_SERVICE_AND_SKU / COST_BY_SERVICE_AND_TAG /
     * COST_BY_COMPARTMENT / MONTHLY_COST
     */
    @NotBlank(message = "报告类型不能为空")
    private String reportType;

    /** Start date (inclusive), format: yyyy-MM-dd */
    @NotBlank(message = "开始日期不能为空")
    private String startDate;

    /** End date (inclusive), format: yyyy-MM-dd */
    @NotBlank(message = "结束日期不能为空")
    private String endDate;

    /**
     * Granularity: DAILY or MONTHLY
     */
    @NotBlank(message = "粒度不能为空")
    private String granularity;

    /**
     * Query filter: COST or USAGE
     */
    private String queryType;
}
