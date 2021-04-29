package com.aiyi.game.dnfserver.advice;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

//@ControllerAdvice
//public class BaseExceptionControllerAdvice {
//
//    @ResponseBody
//    @ExceptionHandler(Exception.class)
//    public Map<String, Object> errorHandler(Exception ex){
//        Map<String, Object> result = new HashMap<>();
//        result.put("message", ex.getMessage());
//        return result;
//    }
//
//}
