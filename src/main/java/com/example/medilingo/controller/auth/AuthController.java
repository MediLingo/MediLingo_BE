package com.example.medilingo.controller.auth;

import com.example.medilingo.controller.auth.request.SignupReqDto;
import com.example.medilingo.controller.auth.response.SignupRespDto;
import com.example.medilingo.service.AuthService;
import com.example.medilingo.util.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/auth/signup")
    public ResponseEntity<ApiResult<SignupRespDto>> signup(@RequestBody SignupReqDto reqDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.success(authService.signup(reqDto)));
    }

}
