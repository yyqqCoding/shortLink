package com.coding.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson2.JSON;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coding.shortlink.admin.common.biz.user.UserContext;
import com.coding.shortlink.admin.common.convention.exception.ClientException;
import com.coding.shortlink.admin.common.convention.exception.ServiceException;
import com.coding.shortlink.admin.common.enums.UserErrorCodeEnum;
import com.coding.shortlink.admin.dao.entity.UserDO;
import com.coding.shortlink.admin.dao.mapper.UserMapper;
import com.coding.shortlink.admin.dto.req.UserLoginReqDTO;
import com.coding.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.coding.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.coding.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.coding.shortlink.admin.dto.resp.UserRespDTO;
import com.coding.shortlink.admin.service.GroupService;
import com.coding.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.coding.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY;
import static com.coding.shortlink.admin.common.constant.RedisCacheConstant.USER_LOGIN_KEY;

import java.util.Map;
import java.util.concurrent.TimeUnit;
@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {


    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final GroupService groupService;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 依据用户名查询信息
     */
    @Override
    public UserRespDTO getUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ServiceException(UserErrorCodeEnum.USER_NULL);
        }
        UserRespDTO result = new UserRespDTO();
        BeanUtils.copyProperties(userDO, result);
        return result;
    }

    /**
     * 查询用户名是否存在
     */
    @Override
    public Boolean hasUsername(String username) {
        // Bloom 过滤器快速判定（带异常自愈：缺配置或丢键时重建并重试一次）
        try {
            if (userRegisterCachePenetrationBloomFilter.contains(username)) {
                return false;
            }
        } catch (RedisException ex) {
            log.warn("Bloom contains failed, will reinit and retry. username={}", username, ex);
            try {
                userRegisterCachePenetrationBloomFilter.tryInit(100000000L, 0.001);
                if (userRegisterCachePenetrationBloomFilter.contains(username)) {
                    return false;
                }
            } catch (Exception ignore) {
                // 忽略，继续走 DB 兜底
            }
        } catch (Exception ex) {
            log.warn("Bloom contains unexpected error, fallback to DB. username={}", username, ex);
        }
        // DB 兜底校验
        boolean existsInDb = this.lambdaQuery().eq(UserDO::getUsername, username).oneOpt().isPresent();
        if (existsInDb) {
            // 回填 Bloom，降低后续穿透
            try {
                userRegisterCachePenetrationBloomFilter.add(username);
            } catch (Exception ignore) {
                // 忽略 Bloom 异常，不影响主流程
            }
            return false;
        }
        return true;
    }

    /**
     * 注册用户
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void register(UserRegisterReqDTO userRegisterReqDTO) {
        //判断用户名是否已存在
        if (!this.hasUsername(userRegisterReqDTO.getUsername())) {
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
            groupService.saveGroup(userRegisterReqDTO.getUsername(), "默认分组");

            //添加用户名到布隆过滤器中
            try {
                userRegisterCachePenetrationBloomFilter.add(userRegisterReqDTO.getUsername());
            } catch (RedisException bfEx) {
                log.warn("Bloom add failed, will reinit and retry. username={}", userRegisterReqDTO.getUsername(), bfEx);
                try {
                    userRegisterCachePenetrationBloomFilter.tryInit(100000000L, 0.001);
                    userRegisterCachePenetrationBloomFilter.add(userRegisterReqDTO.getUsername());
                } catch (Exception ignore) {
                    log.warn("Bloom reinit/add still failed, ignore. username={}", userRegisterReqDTO.getUsername());
                }
            } catch (Exception bfEx) {
                // 记录但不影响主流程提交
                log.warn("Add username to bloom filter failed, username={}", userRegisterReqDTO.getUsername(), bfEx);
            }

        }catch (DuplicateKeyException dkEx) {
            log.error("Register duplicate username, username={}", userRegisterReqDTO.getUsername(), dkEx);
            throw new ServiceException(UserErrorCodeEnum.USER_NAME_EXIST);
        }catch (Exception e) {
            log.error("Register user failed, username={}", userRegisterReqDTO.getUsername(), e);
            throw new ServiceException(UserErrorCodeEnum.USER_SAVE_ERROR);
        }
        finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }
    
    /**
     * 修改用户信息
     */
    @Override
    public void update(UserUpdateReqDTO userUpdateReqDTO) {
        //当前登录用户与修改用户相同才能修改
        if(!userUpdateReqDTO.getUsername().equals(UserContext.getUsername())){
            throw new ClientException("当前登录用户修改请求异常");
        }
        LambdaQueryWrapper<UserDO> updateWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, userUpdateReqDTO.getUsername());
        baseMapper.update(BeanUtil.toBean(userUpdateReqDTO, UserDO.class), updateWrapper);
    }


    /**
     * 用户登录
     */
    @Override
    public UserLoginRespDTO login(UserLoginReqDTO userLoginReqDTO) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                    .eq(UserDO::getUsername, userLoginReqDTO.getUsername())
                    .eq(UserDO::getPassword, userLoginReqDTO.getPassword())
                    .eq(UserDO::getDelFlag, 0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if (userDO == null) {
            throw new ClientException("用户不存在");
        }
        //查询redis中是否存在该用户
        Map<Object,Object> hasLoginMap = stringRedisTemplate.opsForHash().entries(USER_LOGIN_KEY+userLoginReqDTO.getUsername());
        if(CollectionUtil.isNotEmpty(hasLoginMap)){
            //登录成功 设置过期时间 返回token
            stringRedisTemplate.expire(USER_LOGIN_KEY+userLoginReqDTO.getUsername(), 30L, TimeUnit.MINUTES);
            String token = hasLoginMap.keySet().stream()
            .findFirst()
            .map(Object::toString)
            .orElseThrow(() -> new ClientException("登录失败"));
            return new UserLoginRespDTO(token);
        }
        /**
         * Hash
         * Key：login_用户名
         * Value：
         *  Key：token标识
         *  Val：JSON 字符串（用户信息）
         */

        //将用户信息保存到redis中
        String token = UUID.randomUUID().toString();
        stringRedisTemplate.opsForHash().put(USER_LOGIN_KEY+userLoginReqDTO.getUsername(), token, JSON.toJSONString(userDO));
        stringRedisTemplate.expire(USER_LOGIN_KEY+userLoginReqDTO.getUsername(), 30L, TimeUnit.MINUTES);
        return new UserLoginRespDTO(token);

     }
    
    /**
     * 检查用户是否登录
     */
    @Override
    public Boolean checkLogin(String username, String token){
        
        return stringRedisTemplate.opsForHash().get(USER_LOGIN_KEY+username, token) != null;
        
    }

    /**
     * 用户退出登录
     */
    @Override
    public void logout(String username, String token){
        //先检查用户是否登录
        if(checkLogin(username, token)){
            stringRedisTemplate.opsForHash().delete(USER_LOGIN_KEY+username, token);
            return;
        }
        throw new ClientException("用户未登录");
    }
    
}

