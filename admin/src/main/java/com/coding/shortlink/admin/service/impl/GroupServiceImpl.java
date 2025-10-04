package com.coding.shortlink.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.coding.shortlink.admin.dao.entity.GroupDO;
import com.coding.shortlink.admin.dao.entity.GroupUniqueDO;
import com.coding.shortlink.admin.dao.mapper.GroupMapper;
import com.coding.shortlink.admin.dao.mapper.GroupUniqueMapper;
import com.coding.shortlink.admin.service.GroupService;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.coding.shortlink.admin.common.biz.user.UserContext;
import com.coding.shortlink.admin.common.convention.exception.ClientException;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import static com.coding.shortlink.admin.common.constant.RedisCacheConstant.LOCK_GROUP_CREATE_KEY;
import java.util.List;
import org.redisson.api.RBloomFilter;
import com.coding.shortlink.admin.toolkit.RandomGenerator;
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private final RBloomFilter<String> gidRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final GroupUniqueMapper groupUniqueMapper;

    @Value("${short-link.group.max-num}")
    private Integer groupMaxNum;

    @Override
    public void saveGroup(String groupName) {
        saveGroup(UserContext.getUsername(), groupName);
    }

    public void saveGroup(String username, String groupName) {
        //分组创建分布式锁
        RLock lock = redissonClient.getLock(String.format(LOCK_GROUP_CREATE_KEY, username));
        lock.lock();
        try{
            //检查用户创建分组是否超出了最大分组数量
            LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                        .eq(GroupDO::getUsername, username)
                        .eq(GroupDO::getDelFlag, 0);
            List<GroupDO> groupDOList = baseMapper.selectList(queryWrapper);
            if(CollectionUtil.isNotEmpty(groupDOList)&&  groupDOList.size() == groupMaxNum){
                throw new ClientException(String.format("已超出最大分组数：%d", groupMaxNum));
            }

            //创建分组标识GID：
            int retryCount = 0;
            int maxRetryCount = 10;
            String gid = null;
            while (retryCount < maxRetryCount) {
               gid=saveGroupUniqueReturnGid();
               if(StrUtil.isNotEmpty(gid)){
                GroupDO groupDO = GroupDO.builder()
                    .gid(gid)
                    .sortOrder(0)
                    .username(username)
                    .name(groupName)
                    .build();
                baseMapper.insert(groupDO);
                try {
                    gidRegisterCachePenetrationBloomFilter.add(gid);
                } catch (Exception bfEx) {
                    log.warn("Add gid to bloom filter failed, gid={}", gid, bfEx);
                    try {
                        gidRegisterCachePenetrationBloomFilter.tryInit(200000000L, 0.001);
                        gidRegisterCachePenetrationBloomFilter.add(gid);
                    } catch (Exception ignore) {
                        log.warn("Bloom reinit/add still failed for gid={}, ignore.", gid);
                    }
                }
                break;
               }
               retryCount++;
            }
            if(StrUtil.isEmpty(gid)){
                throw new ClientException("分组标识GID生成失败");
            }

        }finally{
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }
    /**
     * 生成分组标识GID
     * @return
     */
    private String saveGroupUniqueReturnGid() {
        String gid = RandomGenerator.generateRandom();
        //判断GID是否已经存在：布隆过滤器
        try {
            if (gidRegisterCachePenetrationBloomFilter.contains(gid)) {
                return null;
            }
        } catch (RedisException ex) {
            log.warn("Bloom contains failed for gid, will reinit and retry. gid={}", gid, ex);
            try {
                gidRegisterCachePenetrationBloomFilter.tryInit(200000000L, 0.001);
                if (gidRegisterCachePenetrationBloomFilter.contains(gid)) {
                    return null;
                }
            } catch (Exception ignore) {
                // 忽略，继续走 DB 唯一表兜底
            }
        } catch (Exception ex) {
            log.warn("Bloom contains unexpected error for gid, fallback to DB unique table. gid={}", gid, ex);
        }
        GroupUniqueDO groupUniqueDO = GroupUniqueDO.builder()
            .gid(gid)
            .build();

        try{
            groupUniqueMapper.insert(groupUniqueDO);
        }catch(Exception e){
            return null;
        }
        return gid;
    }

}
