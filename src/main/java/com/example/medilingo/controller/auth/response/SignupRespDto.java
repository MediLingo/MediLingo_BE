package com.example.medilingo.controller.auth.response;

import com.example.medilingo.domain.member.Member;
import lombok.Getter;

@Getter
public class SignupRespDto {
    private final Long memberId;

    public SignupRespDto(Member member){
        this.memberId = member.getId();
    }
}
