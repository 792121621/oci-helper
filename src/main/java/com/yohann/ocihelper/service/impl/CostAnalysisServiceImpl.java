package com.yohann.ocihelper.service.impl;

import com.oracle.bmc.usageapi.model.RequestSummarizedUsagesDetails;
import com.oracle.bmc.usageapi.model.StaticDateRange;
import com.oracle.bmc.usageapi.model.UsageSummary;
import com.oracle.bmc.usageapi.requests.RequestSummarizedUsagesRequest;
import com.oracle.bmc.usageapi.responses.RequestSummarizedUsagesResponse;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.oci.cost.GetCostAnalysisParams;
import com.yohann.ocihelper.bean.response.oci.cost.CostItemRsp;
import com.yohann.ocihelper.bean.response.oci.cost.GetCostAnalysisRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.exception.OciException;
import com.yohann.ocihelper.service.ICostAnalysisService;
import com.yohann.ocihelper.service.ISysService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Implementation of {@link ICostAnalysisService} using OCI Usage API.
 *
 * @author Yohann
 */
@Service
@Slf4j
public class CostAnalysisServiceImpl implements ICostAnalysisService {

    @Resource
    private ISysService sysService;

    @Override
    public GetCostAnalysisRsp getCostAnalysis(GetCostAnalysisParams params) {
        // Use home region for Usage API (it only supports the tenancy home region endpoint)
        SysUserDTO sysUserDTO = sysService.getOciUser(params.getOciCfgId());
        try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(sysUserDTO)) {
            String tenantId = fetcher.getProvider().getTenantId();

            // Build date range
            Date startTime = Date.from(
                    LocalDate.parse(params.getStartDate()).atStartOfDay().toInstant(ZoneOffset.UTC));
            Date endTime = Date.from(
                    LocalDate.parse(params.getEndDate()).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));

            StaticDateRange dateRange = StaticDateRange.builder()
                    .timeUsageStarted(startTime)
                    .timeUsageEnded(endTime)
                    .build();

            // Map report type to group-by dimensions
            List<String> groupByList = buildGroupBy(params.getReportType());

            // Determine granularity
            RequestSummarizedUsagesDetails.Granularity granularity =
                    "MONTHLY".equalsIgnoreCase(params.getGranularity())
                            ? RequestSummarizedUsagesDetails.Granularity.Monthly
                            : RequestSummarizedUsagesDetails.Granularity.Daily;

            // Determine query type
            RequestSummarizedUsagesDetails.QueryType queryType =
                    "USAGE".equalsIgnoreCase(params.getQueryType())
                            ? RequestSummarizedUsagesDetails.QueryType.Usage
                            : RequestSummarizedUsagesDetails.QueryType.Cost;

            RequestSummarizedUsagesDetails.Builder detailsBuilder = RequestSummarizedUsagesDetails.builder()
                    .tenantId(tenantId)
                    .timeUsageStarted(startTime)
                    .timeUsageEnded(endTime)
                    .granularity(granularity)
                    .queryType(queryType)
                    .groupBy(groupByList)
                    // false = return one row per time-bucket (day/month) per group dimension
                    // true  = collapse all time buckets into one row (totals only, no trend)
                    .isAggregateByTime(false);

            // compartmentDepth is required when any compartment dimension is in groupBy
            boolean needsCompartmentDepth = groupByList.stream()
                    .anyMatch(k -> k.startsWith("compartment"));
            if (needsCompartmentDepth) {
                // depth=1 means the root compartment level (tenancy); higher values go deeper
                detailsBuilder.compartmentDepth(new BigDecimal("1"));
            }

            RequestSummarizedUsagesDetails details = detailsBuilder.build();

            // Paginate through all results
            List<UsageSummary> allItems = new ArrayList<>();
            String page = null;
            do {
                RequestSummarizedUsagesRequest.Builder reqBuilder = RequestSummarizedUsagesRequest.builder()
                        .requestSummarizedUsagesDetails(details);
                if (page != null) {
                    reqBuilder.page(page);
                }
                RequestSummarizedUsagesResponse response =
                        fetcher.getUsageapiClient().requestSummarizedUsages(reqBuilder.build());
                List<UsageSummary> items = response.getUsageAggregation().getItems();
                if (items != null) {
                    allItems.addAll(items);
                }
                page = response.getOpcNextPage();
            } while (page != null);

            // Map to response DTOs
            List<CostItemRsp> rspItems = new ArrayList<>();
            BigDecimal totalCost = BigDecimal.ZERO;
            String currency = null;

            for (UsageSummary item : allItems) {
                CostItemRsp rsp = new CostItemRsp();
                rsp.setService(item.getService());
                // Use skuPartNumber as description (product description)
                rsp.setDescription(item.getSkuPartNumber());
                rsp.setSkuName(item.getSkuName());
                rsp.setCompartmentName(item.getCompartmentName());
                rsp.setRegion(item.getRegion());
                rsp.setUnit(item.getUnit());

                // Format date
                if (item.getTimeUsageStarted() != null) {
                    String dateStr = new java.text.SimpleDateFormat(
                            "MONTHLY".equalsIgnoreCase(params.getGranularity()) ? "yyyy-MM" : "yyyy-MM-dd")
                            .format(item.getTimeUsageStarted());
                    rsp.setDate(dateStr);
                }

                // Cost
                BigDecimal cost = item.getComputedAmount() != null
                        ? item.getComputedAmount()
                        : BigDecimal.ZERO;
                rsp.setCost(cost);
                totalCost = totalCost.add(cost);

                // Quantity
                rsp.setComputedQuantity(item.getComputedQuantity());

                // Currency: trim to guard against blank-space strings returned by OCI free-tier
                String itemCurrency = item.getCurrency() != null ? item.getCurrency().trim() : null;
                if (itemCurrency != null && !itemCurrency.isEmpty()) {
                    rsp.setCurrency(itemCurrency);
                    if (currency == null) {
                        currency = itemCurrency;
                    }
                }

                rspItems.add(rsp);
            }

            GetCostAnalysisRsp rsp = new GetCostAnalysisRsp();
            rsp.setTotal(rspItems.size());
            // Fallback: free-tier accounts may return null currency in all rows
            rsp.setCurrency(currency != null ? currency : "USD");
            rsp.setTotalCost(totalCost);
            rsp.setItems(rspItems);
            return rsp;

        } catch (OciException oe) {
            throw oe;
        } catch (Exception e) {
            log.error("查询成本分析失败", e);
            throw new OciException(-1, "查询成本分析失败：" + e.getMessage());
        }
    }

    /**
     * Build groupBy dimension list based on report type.
     * Valid groupBy keys (from OCI API docs):
     * tagNamespace, tagKey, tagValue, service, skuName, skuPartNumber,
     * unit, compartmentName, compartmentPath, compartmentId,
     * platform, region, logicalAd, resourceId, tenantId, tenantName
     */
    private List<String> buildGroupBy(String reportType) {
        return switch (reportType) {
            // Group by service + product description (skuPartNumber)
            case "COST_BY_SERVICE_AND_DESCRIPTION" -> List.of("service", "skuPartNumber");
            // Group by service + SKU name
            case "COST_BY_SERVICE_AND_SKU" -> List.of("service", "skuName");
            // Group by service + tag namespace/key
            case "COST_BY_SERVICE_AND_TAG" -> List.of("service", "tagNamespace", "tagKey");
            // Group by compartment name only
            case "COST_BY_COMPARTMENT" -> List.of("compartmentName");
            // Monthly cost – group by service only
            case "MONTHLY_COST" -> List.of("service");
            // Default: COST_BY_SERVICE
            default -> List.of("service");
        };
    }
}
