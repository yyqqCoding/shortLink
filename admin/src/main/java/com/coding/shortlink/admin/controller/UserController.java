package com.coding.shortlink.admin.controller;

import com.coding.shortlink.admin.common.convention.result.Result;
import com.coding.shortlink.admin.common.convention.result.Results;
import com.coding.shortlink.admin.dto.resp.UserRespDTO;
import com.coding.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户登录接口
 */
@RestController
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /*
     * 依据用户名查询用户信息
     */
    @GetMapping("/api/short-link/admin/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username) {
        return Results.success(userService.getUserByUsername(username));
    }


}
