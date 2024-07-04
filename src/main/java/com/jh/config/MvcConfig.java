package com.jh.config;

import com.jh.utils.LoginInterceptor;
import com.jh.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**",
                        "/shop/**",
                        "/user/code",
                        "/user/login",
                        "/blog/hot"
                        ).order(1);
        //拦截所有,order控制拦截顺序，默认都为0按照编写顺序拦截
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
