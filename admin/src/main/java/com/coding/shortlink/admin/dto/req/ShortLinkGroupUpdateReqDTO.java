package com.coding.shortlink.admin.dto.req;

import lombok.Data;

/**
 * 修改短链接分组名称请求参数
 */
@Data
public class ShortLinkGroupUpdateReqDTO {
    /**
     * 分组标识GID
     */
    private String gid;
    
    /*
     * 分组名称
     */
    private String name;
}
