package com.hawa.hawa_backend.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Maps camelCase sort property names from the API (e.g. createdAt) to
 * snake_case column names used in native SQL queries (e.g. created_at).
 */
public final class NativeSortUtil {

    private NativeSortUtil() {}

    public static Pageable toNativeSortPageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        var mappedOrders = pageable.getSort().stream()
                .map(order -> {
                    String mapped = switch (order.getProperty()) {
                        case "createdAt" -> "created_at";
                        case "updatedAt" -> "updated_at";
                        case "finishedAt" -> "finished_at";
                        case "firstName" -> "first_name";
                        case "lastName" -> "last_name";
                        case "companyId" -> "company_id";
                        case "userId" -> "user_id";
                        case "brandId" -> "brand_id";
                        case "reportId" -> "report_id";
                        case "brandName" -> "brand_name";
                        case "dataSource" -> "data_source";
                        case "dateFrom" -> "date_from";
                        case "dateTo" -> "date_to";
                        case "score", "status", "summary", "email", "role",
                             "industry", "keyword" -> order.getProperty();
                        default -> throw new IllegalArgumentException(
                                "Unsupported sort property: " + order.getProperty());
                    };
                    return new Sort.Order(order.getDirection(), mapped);
                })
                .toList();
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(mappedOrders));
    }
}
