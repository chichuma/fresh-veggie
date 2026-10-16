package com.freshveggie.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.freshveggie.constant.MessageConstant;
import com.freshveggie.constant.PasswordConstant;
import com.freshveggie.constant.StatusConstant;
import com.freshveggie.dto.StaffDTO;
import com.freshveggie.dto.StaffLoginDTO;
import com.freshveggie.dto.pageQuery.StaffPageQueryDTO;
import com.freshveggie.entity.Staff;
import com.freshveggie.exception.AccountLockedException;
import com.freshveggie.exception.AccountNotFoundException;
import com.freshveggie.exception.BusinessException;
import com.freshveggie.exception.PasswordErrorException;
import com.freshveggie.mapper.StaffMapper;
import com.freshveggie.result.PageResult;
import com.freshveggie.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {

    private final StaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Staff login(StaffLoginDTO staffLoginDTO) {
        Staff staff = staffMapper.selectOne(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getUsername, staffLoginDTO.getUsername()));
        if (staff == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!passwordEncoder.matches(staffLoginDTO.getPassword(), staff.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }
        if (Objects.equals(staff.getStatus(), StatusConstant.DISABLE)) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }
        return staff;
    }

    @Override
    public boolean save(StaffDTO staffDTO) {
        Long count = staffMapper.selectCount(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getUsername, staffDTO.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException(staffDTO.getUsername() + "用户名已被注册");
        }
        Staff staff = new Staff();
        BeanUtils.copyProperties(staffDTO, staff);
        staff.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));
        return staffMapper.insert(staff) > 0;
    }

    @Override
    public PageResult<Staff> listStaff(StaffPageQueryDTO staffPageQueryDTO) {
        Page<Staff> page = new Page<>(staffPageQueryDTO.getPage(), staffPageQueryDTO.getPageSize());
        Page<Staff> result = staffMapper.selectPage(page, Wrappers.<Staff>lambdaQuery()
                .like(staffPageQueryDTO.getName() != null, Staff::getName, staffPageQueryDTO.getName()));
        return toPageResult(result);
    }

    @Override
    public void changeStaffStatus(Integer status, Long id) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        staff.setStatus(status);
        staffMapper.updateById(staff);
    }

    @Override
    public Staff getStaffById(Long id) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        return staff;
    }

    @Override
    public void updateStaff(StaffDTO staffDTO) {
        Staff staff = getStaffById(staffDTO.getId());
        BeanUtils.copyProperties(staffDTO, staff);
        staffMapper.updateById(staff);
    }

    private <T> PageResult<T> toPageResult(Page<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords(), page.getSize(), page.getCurrent());
    }
}
