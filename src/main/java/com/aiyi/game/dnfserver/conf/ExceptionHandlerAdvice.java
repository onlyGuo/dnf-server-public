package com.aiyi.game.dnfserver.conf;

import com.aiyi.core.beans.ResultBean;
import com.aiyi.core.exception.AccessOAuthException;
import com.aiyi.core.exception.FlowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * @Author: 栾晨光
 * @Date: 2019-09-25
 * @Time: 11:30
 * @Description: 统一异常处理类
 */
@RestControllerAdvice
public class ExceptionHandlerAdvice {

    /**
     * MVCLogger对象托管容器
     */
    private Map<String, Logger> loggerMap = new HashMap<>();

    /**
     * 获取发生异常方法中的Logger对象
     * @param clazz
     *      异常方法ClazzStr
     * @return
     */
    private Logger getLogger(String clazz){
        Logger logger = loggerMap.get(clazz);
        if (null == logger){
            logger = LoggerFactory.getLogger(clazz);
            loggerMap.put(clazz, logger);
        }
        return logger;
    }


    @ExceptionHandler(value = Exception.class)
    public ResultBean defaultErrorHandler(HttpServletRequest req, HttpServletResponse rep, Exception e) throws Exception {
        getLogger(e.getStackTrace()[0].getClassName()).error(StringUtils.isEmpty(e.getMessage()) ? "未知异常." : e.getMessage() ,e);
        rep.setStatus(500);
        return ResultBean.error(StringUtils.isEmpty(e.getMessage()) ? "未知异常" : e.getMessage())
                .setCode(500)
                .setResponseBody(e.getStackTrace());
    }

    /**
     * 登录权限认证异常401
     * @param e
     * @return
     */
    @ExceptionHandler(value = AccessOAuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ResponseBody
    public ResultBean accessOAuthException(Exception e, HttpServletRequest request, HttpServletResponse response) {
//        String header = request.getHeader("Content-Type");
//        if (header != null && header.toLowerCase().contains("application/json")){
            return ResultBean.error("没有权限.").setCode(HttpStatus.UNAUTHORIZED.value());
//        }else{
//            try {
//                response.sendRedirect(request.getContextPath() + "/");
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
//            return null;
//        }
    }

    /**
     * 业务异常400
     * @param e
     *      异常信息
     * @return
     */
    @ExceptionHandler(value = FlowException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ResultBean flowException(Exception e) {
        return ResultBean.error(e.getMessage()).setCode(HttpStatus.BAD_REQUEST.value());
    }

    @ExceptionHandler(value = IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ResultBean illegalArgumentExceptionException(Exception e) {
        return ResultBean.error(e.getMessage()).setCode(HttpStatus.BAD_REQUEST.value());
    }

}