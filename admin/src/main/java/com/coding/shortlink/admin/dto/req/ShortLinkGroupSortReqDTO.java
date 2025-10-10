package com.coding.shortlink.admin.dto.req;

import lombok.Data;

@Data
/**
 * 排序短链接分组请求参数
 */
public class ShortLinkGroupSortReqDTO {

    /**
     * 分组标识GID
     */
    private String gid;

    /**
     * 排序
     */
    private Integer sortOrder;
}
