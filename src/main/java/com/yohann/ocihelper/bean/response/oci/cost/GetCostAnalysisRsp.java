package com.yohann.ocihelper.bean.response.oci.cost;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response wrapper for Cost Analysis query.
 *
 * @author Yohann
 */
@Data
public class GetCostAnalysisRsp {

    /** Total number of records */
    private int total;

    /** Currency code (from first record), e.g. "USD", "SGD" */
    private String currency;

    /** Total cost across all returned records */
    private BigDecimal totalCost;

    /** Cost item list */
    private List<CostItemRsp> items;
}
