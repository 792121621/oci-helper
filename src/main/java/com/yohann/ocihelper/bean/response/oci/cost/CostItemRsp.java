package com.yohann.ocihelper.bean.response.oci.cost;

import lombok.Data;

import java.math.BigDecimal;

/**
 * A single cost item row returned from OCI Usage API.
 *
 * @author Yohann
 */
@Data
public class CostItemRsp {

    /** Service name, e.g. "COMPUTE" */
    private String service;

    /** SKU / description */
    private String description;

    /** Resource name / SKU part number */
    private String skuName;

    /** Compartment name */
    private String compartmentName;

    /** Region */
    private String region;

    /** Date (DAILY: yyyy-MM-dd, MONTHLY: yyyy-MM) */
    private String date;

    /** Computed currency cost */
    private BigDecimal cost;

    /** Unit cost */
    private BigDecimal computedQuantity;

    /** Currency code, e.g. "USD", "SGD" */
    private String currency;

    /** Unit of measure */
    private String unit;
}
