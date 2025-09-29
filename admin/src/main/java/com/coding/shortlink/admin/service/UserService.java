package com.coding.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.coding.shortlink.admin.dao.entity.UserDO;
import com.coding.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户服务
 */

public interface UserService extends IService<UserDO> {

    /**
     * 依据用户名查询用户信息
     */
    UserRespDTO getUserByUsername(String username);

}
