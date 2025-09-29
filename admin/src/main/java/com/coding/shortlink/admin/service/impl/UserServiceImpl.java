package com.coding.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coding.shortlink.admin.common.convention.exception.ServiceException;
import com.coding.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.coding.shortlink.admin.dao.entity.UserDO;
import com.coding.shortlink.admin.dao.mapper.UserMapper;
import com.coding.shortlink.admin.dto.resp.UserRespDTO;
import com.coding.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {

    /**
     * 依据用户名查询信息
     */
    @Override
    public UserRespDTO getUserByUsername(String username) {
        UserDO userDO = this.getOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (userDO == null) {
            //用户不存在
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO userRespDTO = new UserRespDTO();
        BeanUtil.copyProperties(userDO, userRespDTO);
        return userRespDTO;
    }
}
