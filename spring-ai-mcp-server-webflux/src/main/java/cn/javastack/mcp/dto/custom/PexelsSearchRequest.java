package cn.javastack.mcp.dto.custom;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pexels 图片搜索请求
 */
@Data
public class PexelsSearchRequest {

    private static final int MIN_PER_PAGE = 3;
    private static final int MAX_PER_PAGE = 80;
    private static final int DEFAULT_PER_PAGE = 3;

    /**
     * 图片搜索问题描述
     */
    private String message;

    /**
     * 图片方向，如 landscape/portrait/square
     */
    private String orientation = "";

    /**
     * 图片最小尺寸，如 large/medium/small
     */
    private String size = "";

    /**
     * 图片颜色，如 blue/red，或十六进制色值 #ffffff
     */
    private String color = "";

    /**
     * 搜索语言地区，如 en-US/zh-CN
     */
    private String locale = "";

    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页数量
     */
    private Integer perPage = 3;

    /**
     * 转换为 PexelsAPI 查询参数
     */
    public Map<String, Object> toQueryParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", getMessage());
        params.put("orientation", getOrientation());
        params.put("size", getSize());
        params.put("color", getColor());
        params.put("locale", getLocale());
        params.put("page", normalizePage(getPage()));
        params.put("per_page", normalizePerPage(getPerPage()));
        return params;
    }

    /**
     * 统一兜底页码范围
     */
    private int normalizePage(Integer requestedPage) {
        if (requestedPage == null || requestedPage < 1) {
            return 1;
        }
        return requestedPage;
    }

    /**
     * 统一兜底数量范围
     */
    private int normalizePerPage(Integer requestedPerPage) {
        if (requestedPerPage == null) {
            return DEFAULT_PER_PAGE;
        }
        return Math.clamp(requestedPerPage, MIN_PER_PAGE, MAX_PER_PAGE);
    }
}
