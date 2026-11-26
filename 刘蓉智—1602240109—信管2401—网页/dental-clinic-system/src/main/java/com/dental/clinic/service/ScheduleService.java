package com.dental.clinic.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dental.clinic.common.BusinessException;
import com.dental.clinic.entity.DentalChair;
import com.dental.clinic.entity.Schedule;
import com.dental.clinic.entity.Store;
import com.dental.clinic.entity.WorkCalendar;
import com.dental.clinic.mapper.DentalChairMapper;
import com.dental.clinic.mapper.ScheduleMapper;
import com.dental.clinic.mapper.StoreMapper;
import com.dental.clinic.mapper.WorkCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班服务（管理端）：
 * 号源容量不超过门店可用牙椅数；同一医生不可同时在两个诊所出诊
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final StoreMapper storeMapper;
    private final DentalChairMapper chairMapper;
    private final WorkCalendarMapper calendarMapper;

    /** 新建排班（草稿），带全部冲突校验 */
    @Transactional
    public Schedule create(Schedule s) {
        Store store = storeMapper.selectById(s.getStoreId());
        if (store == null || store.getStatus() == null || store.getStatus() != 1) {
            throw new BusinessException("门店不存在或已停业");
        }
        if (s.getWorkDate() == null || s.getTimeSlot() == null || s.getDoctorId() == null) {
            throw new BusinessException("缺少排班必填项（医生/日期/时段）");
        }
        if (s.getWorkDate().isBefore(LocalDate.now())) {
            throw new BusinessException("排班日期不能早于今天");
        }

        // 节假日校验：法定节假日不可排班
        WorkCalendar cal = calendarMapper.selectOne(new LambdaQueryWrapper<WorkCalendar>()
                .eq(WorkCalendar::getStoreId, s.getStoreId())
                .eq(WorkCalendar::getCalDate, s.getWorkDate()));
        if (cal != null && cal.getDayType() != null && cal.getDayType() == 2) {
            throw new BusinessException("所选日期为法定节假日，不可排班（工作日历）");
        }

        // 牙椅校验：必须属于本门店且在用
        long chairCount = chairMapper.selectCount(new LambdaQueryWrapper<DentalChair>()
                .eq(DentalChair::getStoreId, s.getStoreId())
                .eq(DentalChair::getStatus, 1));
        if (chairCount == 0) {
            throw new BusinessException("门店没有可用牙椅，无法排班");
        }
        if (s.getChairId() != null) {
            DentalChair chair = chairMapper.selectById(s.getChairId());
            if (chair == null || chair.getStatus() == null || chair.getStatus() != 1) {
                throw new BusinessException("指定牙椅不可用（维修中或已报废）");
            }
            if (!chair.getStoreId().equals(s.getStoreId())) {
                throw new BusinessException("牙椅不属于该门店");
            }
        }

        // 冲突校验 1：同一医生同一天同一时段不可在两个诊所出诊
        Long doctorConflict = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, s.getDoctorId())
                .eq(Schedule::getWorkDate, s.getWorkDate())
                .eq(Schedule::getTimeSlot, s.getTimeSlot())
                .ne(Schedule::getStatus, 2));
        if (doctorConflict > 0) {
            throw new BusinessException("冲突：该医生当日该时段已有排班（同一医生不可同时在两个诊所出诊）");
        }

        // 冲突校验 2：同一把牙椅同时段不可分配给两位医生
        if (s.getChairId() != null) {
            Long chairConflict = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                    .eq(Schedule::getChairId, s.getChairId())
                    .eq(Schedule::getWorkDate, s.getWorkDate())
                    .eq(Schedule::getTimeSlot, s.getTimeSlot())
                    .ne(Schedule::getStatus, 2));
            if (chairConflict > 0) {
                throw new BusinessException("冲突：该牙椅当日该时段已被占用");
            }
        }

        // 号源容量约束：不超过门店可用牙椅数
        if (s.getQuotaTotal() == null || s.getQuotaTotal() <= 0) {
            throw new BusinessException("号源容量必须为正数");
        }
        if (s.getQuotaTotal() > chairCount) {
            throw new BusinessException("号源容量(" + s.getQuotaTotal() + ")超过门店可用牙椅数(" + chairCount + ")");
        }

        s.setQuotaUsed(0);
        s.setStatus(0); // 草稿
        scheduleMapper.insert(s);
        return s;
    }

    /** 发布排班（草稿 -> 已发布，患者可见） */
    @Transactional
    public Schedule publish(Long scheduleId) {
        Schedule s = scheduleMapper.selectById(scheduleId);
        if (s == null) {
            throw new BusinessException("排班不存在");
        }
        if (s.getStatus() != 0) {
            throw new BusinessException("仅草稿状态可发布");
        }
        s.setStatus(1);
        scheduleMapper.updateById(s);
        return s;
    }

    /** 停用排班 */
    @Transactional
    public Schedule stop(Long scheduleId) {
        Schedule s = scheduleMapper.selectById(scheduleId);
        if (s == null) {
            throw new BusinessException("排班不存在");
        }
        if (s.getStatus() != 1) {
            throw new BusinessException("仅已发布状态可停用");
        }
        if (s.getQuotaUsed() != null && s.getQuotaUsed() > 0) {
            throw new BusinessException("该排班已有 " + s.getQuotaUsed() + " 个预约，需先处理预约再停用");
        }
        s.setStatus(2);
        scheduleMapper.updateById(s);
        return s;
    }

    /** 查询排班（可按门店 + 日期过滤，仅返回已发布） */
    public List<Schedule> listPublished(Long storeId, LocalDate date) {
        LambdaQueryWrapper<Schedule> qw = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getStatus, 1);
        if (storeId != null) {
            qw.eq(Schedule::getStoreId, storeId);
        }
        if (date != null) {
            qw.eq(Schedule::getWorkDate, date);
        }
        qw.orderByAsc(Schedule::getWorkDate).orderByAsc(Schedule::getTimeSlot);
        return scheduleMapper.selectList(qw);
    }

    /** 管理端查询全部排班 */
    public List<Schedule> listAll(Long storeId) {
        LambdaQueryWrapper<Schedule> qw = new LambdaQueryWrapper<Schedule>()
                .orderByDesc(Schedule::getWorkDate);
        if (storeId != null) {
            qw.eq(Schedule::getStoreId, storeId);
        }
        return scheduleMapper.selectList(qw);
    }
}
