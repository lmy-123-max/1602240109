package com.dental.clinic.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.Result;
import com.dental.clinic.entity.Notice;
import com.dental.clinic.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 公告管理：首页就诊通知，管理员可发布/下架 */
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeMapper noticeMapper;
    private final com.dental.clinic.service.OpLogService opLogService;

    /** 已发布公告列表（患者首页可见） */
    @GetMapping("/published")
    public Result<List<Notice>> published() {
        return Result.ok(noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .orderByDesc(Notice::getPublishTime)));
    }

    /** 全部公告（管理端） */
    @GetMapping("/all")
    public Result<List<Notice>> all() {
        return Result.ok(noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .orderByDesc(Notice::getPublishTime)));
    }

    /** 发布公告（管理员） */
    @PostMapping("/publish")
    public Result<Notice> publish(@RequestBody Notice notice) {
        notice.setNoticeId(null);
        notice.setPublishTime(LocalDateTime.now());
        if (notice.getStatus() == null) {
            notice.setStatus(1);
        }
        noticeMapper.insert(notice);
        opLogService.record(notice.getPublisherId(), null, "ADD", "公告",
                notice.getTitle(), "发布公告（面向患者端展示）");
        return Result.ok(notice);
    }

    /** 下架公告 */
    @PostMapping("/{id}/offline")
    public Result<String> offline(@PathVariable Long id) {
        Notice n = noticeMapper.selectById(id);
        if (n == null) {
            return Result.fail("公告不存在");
        }
        n.setStatus(0);
        noticeMapper.updateById(n);
        opLogService.record(null, "管理员", "OFF", "公告",
                n.getTitle(), "公告下架（患者端不再展示）");
        return Result.ok("已下架");
    }
}
