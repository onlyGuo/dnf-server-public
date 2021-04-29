package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.NoticeDao;
import com.aiyi.game.dnfserver.entity.Notice;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
@RequestMapping("api/v1/notice")
public class NoticeController {

    @Resource
    private NoticeDao noticeDao;

    @GetMapping
    @NoLogin
    public Notice getNotice(){
        Notice notice = noticeDao.get(1);
        notice.setContent(new String(Base64.getDecoder().decode(notice.getContent()), StandardCharsets.UTF_8));
        return notice;
    }

    @PutMapping
    public void setNotice(@RequestBody Notice notice){
        notice.setContent(Base64.getEncoder().encodeToString(notice.getContent().getBytes(StandardCharsets.UTF_8)));
        noticeDao.update(notice);
    }

}
