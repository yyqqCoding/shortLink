package com.coding.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.coding.shortlink.admin.dao.entity.UserDO;
import com.coding.shortlink.admin.dto.req.UserLoginReqDTO;
import com.coding.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.coding.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.coding.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.coding.shortlink.admin.dto.resp.UserRespDTO;

/**
 * 用户服务
 */

public interface UserService extends IService<UserDO> {

    /**
     * 依据用户名查询用户信息
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 查询用户名是否存在
     */
    Boolean hasUsername(String username);

    /**
     * 注册用户
     * @param userRegisterReqDTO
     */
    void register(UserRegisterReqDTO userRegisterReqDTO);
    
    /**
     * 修改用户
     * @param userUpdateReqDTO
     */
    void update(UserUpdateReqDTO userUpdateReqDTO);

    
    /**
     * 用户登录
     * @param userLoginReqDTO
     */
    UserLoginRespDTO login(UserLoginReqDTO userLoginReqDTO);

    /*
     * 检查用户是否登录
     * @param username
     * @param token
     * @return
     */
    Boolean checkLogin(String username, String token);

    /*
     * 用户退出登录
     * @param username
     * @param token
     */
    void logout(String username, String token);

}
