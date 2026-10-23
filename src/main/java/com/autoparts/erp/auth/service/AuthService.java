
package com.autoparts.erp.auth.service;

import com.autoparts.erp.auth.dto.LoginRequest;
import com.autoparts.erp.auth.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
