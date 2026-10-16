package com.freshveggie.service;

import com.freshveggie.dto.StaffDTO;
import com.freshveggie.dto.StaffLoginDTO;
import com.freshveggie.dto.pageQuery.StaffPageQueryDTO;
import com.freshveggie.entity.Staff;
import com.freshveggie.result.PageResult;

public interface StaffService {

    Staff login(StaffLoginDTO staffLoginDTO);

    boolean save(StaffDTO staffDTO);

    PageResult<Staff> listStaff(StaffPageQueryDTO staffPageQueryDTO);

    void changeStaffStatus(Integer status, Long id);

    Staff getStaffById(Long id);

    void updateStaff(StaffDTO staffDTO);

}
