package com.example.medilingo.controller.auth.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupReqDto {

    private String name;

    private String email;

    private String password;

    private String nickname;

}
