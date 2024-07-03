package com.jh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jh.dto.LoginFormDTO;
import com.jh.dto.Result;
import com.jh.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);
}
