package com.coding.shortlink.admin.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coding.shortlink.admin.common.convention.result.Result;
import com.coding.shortlink.admin.common.convention.result.Results;
import com.coding.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.coding.shortlink.admin.service.GroupService;

import lombok.RequiredArgsConstructor;

/**
 * 短链接分组控制
 */
@RestController
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    /**
     * 创建短链接分组
     */
    @PostMapping("/api/short-link/admin/v1/group")
    public Result<Void> createGroup(@RequestBody ShortLinkGroupSaveReqDTO shortLinkGroupSaveReqDTO) {
        groupService.saveGroup(shortLinkGroupSaveReqDTO.getName());
        return Results.success();
    }
}
