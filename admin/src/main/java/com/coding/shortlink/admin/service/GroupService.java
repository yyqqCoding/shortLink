package com.coding.shortlink.admin.service;

import java.util.List;
import com.coding.shortlink.admin.dao.entity.GroupDO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.coding.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.coding.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.coding.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;

public interface GroupService extends IService<GroupDO> {

    /**
     * 创建短链接分组
     * @param username
     * @param groupName
     */
    void saveGroup(String groupName,String username);


    /**
     * 创建短链接分组
     * @param groupName
     */

    void saveGroup(String groupName);

    /**
     * 查询短链接分组集合
     * @return
     */
    List<ShortLinkGroupRespDTO> listGroup();


    /**
     * 修改短链接分组名称
     * @param shortLinkGroupUpdateReqDTO
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO shortLinkGroupUpdateReqDTO);


    /**
     * 删除短链接分组
     * @param gid
     */
    void deleteGroup(String gid);

    
    /**
     * 排序短链接分组
     * @param requestParam
     */
    void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam);

}
