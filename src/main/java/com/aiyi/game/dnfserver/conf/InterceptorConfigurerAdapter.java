package com.aiyi.game.dnfserver.conf;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author gsk
 * 拦截器配置
 */
@Configuration
public class InterceptorConfigurerAdapter implements WebMvcConfigurer {

    @Resource
    private RequestThreadFilterConf requestThreadFilterConf;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestThreadFilterConf).excludePathPatterns("/static/**")
                .excludePathPatterns("/error").addPathPatterns("/**");
    }

    /**
     * 添加静态资源文件，外部可以直接访问地址
     * @param registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

}
