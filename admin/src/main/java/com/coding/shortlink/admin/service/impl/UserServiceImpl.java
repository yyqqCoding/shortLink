package com.coding.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coding.shortlink.admin.common.convention.exception.ServiceException;
import com.coding.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.coding.shortlink.admin.dao.entity.UserDO;
import com.coding.shortlink.admin.dao.mapper.UserMapper;
import com.coding.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.coding.shortlink.admin.dto.resp.UserRespDTO;
import com.coding.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.coding.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {


    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;

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

    /**
     * 查询用户名是否存在
     */
    @Override
    public Boolean hasUsername(String username) {
        /**
         * 放置在布隆过滤器中，用户快速判断是否存在该用户名
         */
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    /**
     * 注册用户
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void register(UserRegisterReqDTO userRegisterReqDTO) {
        //判断用户名是否已存在
        if (this.hasUsername(userRegisterReqDTO.getUsername())) {
            throw new ServiceException(UserErrorCodeEnum.USER_NAME_EXIST);
        }  
        //获取分布式锁来创建用户
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY+userRegisterReqDTO.getUsername());
        if(!lock.tryLock()) {
            throw new ServiceException(UserErrorCodeEnum.USER_NAME_EXIST);
        }
        try {
            int insert = baseMapper.insert(BeanUtil.toBean(userRegisterReqDTO, UserDO.class));
            if(insert <= 0) {
                throw new ServiceException(UserErrorCodeEnum.USER_SAVE_ERROR);
            }
            //创建用户成功后，自动创建一个短链接分组（GID）
            groupService.saveGroup(requestParam.getUsername(), "默认分组");

            //添加用户名到布隆过滤器中
            userRegisterCachePenetrationBloomFilter.add(userRegisterReqDTO.getUsername());

        }catch (Exception e) {
            throw new ServiceException(UserErrorCodeEnum.USER_EXIST);
        }
        finally {
            lock.unlock();
        }

    }
}
