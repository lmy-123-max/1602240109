package com.dental.clinic.service;

import com.dental.clinic.entity.SysOpLog;
import com.dental.clinic.mapper.SysOpLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作留痕服务：关键业务增减动作统一落库，任何失败不影响主业务（尽力而为）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpLogService {

    private final SysOpLogMapper opLogMapper;

    public void record(Long operatorId, String operatorName, String action,
                       String targetType, String targetDesc, String detail) {
        try {
            SysOpLog l = new SysOpLog();
            l.setOperatorId(operatorId);
            l.setOperatorName(operatorName == null ? "系统" : operatorName);
            l.setAction(action);
            l.setTargetType(targetType);
            l.setTargetDesc(targetDesc);
            l.setDetail(detail);
            l.setOpTime(LocalDateTime.now());
            opLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("操作留痕写入失败（不影响主业务）: {}", e.getMessage());
        }
    }
}
