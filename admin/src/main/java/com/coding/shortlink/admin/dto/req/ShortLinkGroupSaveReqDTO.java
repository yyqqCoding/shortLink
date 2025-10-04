package com.coding.shortlink.admin.dto.req;

import lombok.Data;

/*
 * 创建短链接分组
 */
@Data
public class ShortLinkGroupSaveReqDTO {

    /**
     * 分组名
     */
    private String name;
}
