package com.jh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jh.dto.LoginFormDTO;
import com.jh.dto.Result;
import com.jh.dto.UserDTO;
import com.jh.entity.User;
import com.jh.mapper.UserMapper;
import com.jh.service.IUserService;
import com.jh.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.jh.utils.RedisConstants.*;
import static com.jh.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 保存验证码,session实现不能共享
        // session.setAttribute("code", code);
        // 设置过期时间为2分钟 set key value ex 120
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 模拟发送短信
        log.debug(
                "发送验证码成功，验证码：{}", code
        );
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机格式错误");
        }
        // Object cacheCode = session.getAttribute("code");
        // 查询redis
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        // 用户信息查询
        User user = query().eq("phone", phone).one();
        // 判断用户是否存在
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 防止敏感信息泄露，将user转为UserDTO
        // session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 将Long id也转成String
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));

        // 1、随机生成token，作为key
        String token = UUID.randomUUID().toString(true);
        String tokenKey =  LOGIN_USER_KEY + token;
        // 2、putAll可以存放一个map
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 3、设置有效期 由于每次访问都需要刷新时间，只设置下面这行那么过了时间就会清除
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
