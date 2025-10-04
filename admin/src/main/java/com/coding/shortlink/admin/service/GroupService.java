package com.coding.shortlink.admin.service;


public interface GroupService {

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

}
