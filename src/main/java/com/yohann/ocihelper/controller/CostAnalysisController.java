package com.yohann.ocihelper.controller;

import com.yohann.ocihelper.bean.ResponseData;
import com.yohann.ocihelper.bean.params.oci.cost.GetCostAnalysisParams;
import com.yohann.ocihelper.bean.response.oci.cost.GetCostAnalysisRsp;
import com.yohann.ocihelper.service.ICostAnalysisService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for OCI Cost Analysis queries.
 *
 * @author Yohann
 */
@RestController
@RequestMapping("/api/cost")
public class CostAnalysisController {

    @Resource
    private ICostAnalysisService costAnalysisService;

    /**
     * Query cost/usage data from OCI Usage API.
     *
     * @param params request body containing ociCfgId, reportType, startDate, endDate, granularity
     * @return cost analysis data grouped by service/sku/etc.
     */
    @PostMapping("/analysis")
    public ResponseData<GetCostAnalysisRsp> analysis(@Validated @RequestBody GetCostAnalysisParams params) {
        return ResponseData.successData(costAnalysisService.getCostAnalysis(params), "查询成本分析成功");
    }
}
