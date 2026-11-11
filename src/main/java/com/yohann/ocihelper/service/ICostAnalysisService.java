package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.params.oci.cost.GetCostAnalysisParams;
import com.yohann.ocihelper.bean.response.oci.cost.GetCostAnalysisRsp;

/**
 * Service interface for querying OCI Cost Analysis (Usage API).
 *
 * @author Yohann
 */
public interface ICostAnalysisService {

    /**
     * Query cost/usage data from OCI Usage API.
     *
     * @param params query parameters (ociCfgId, reportType, startDate, endDate, granularity, queryType)
     * @return cost analysis response with items and summary
     */
    GetCostAnalysisRsp getCostAnalysis(GetCostAnalysisParams params);
}
