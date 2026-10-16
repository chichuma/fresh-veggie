package com.freshveggie.service;

import com.freshveggie.vo.UserLoginVO;

public interface UserService {

    UserLoginVO login(String code);
}
