package com.example.medilingo.service;

import com.example.medilingo.controller.auth.request.SignupReqDto;
import com.example.medilingo.controller.auth.response.SignupRespDto;
import com.example.medilingo.domain.member.Member;
import com.example.medilingo.domain.member.repository.MemberRepository;
import com.example.medilingo.ex.BusinessException;
import com.example.medilingo.ex.ErrorCode;
import com.example.medilingo.security.entity.UserRole;
import com.example.medilingo.security.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public SignupRespDto signup(SignupReqDto dto){
        if (memberRepository.existsByEmail(dto.getEmail())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXIST);
        }

        String encodePassword = passwordEncoder.encode(dto.getPassword());

        Member member = Member.builder()
                .name(dto.getName())
                .nickname(dto.getNickname())
                .password(encodePassword)
                .email(dto.getEmail())
                .role(UserRole.USER)
                .build();

        Member savedMember = memberRepository.save(member);
        String accessToken = jwtUtil.createToken(savedMember.getId(), savedMember.getEmail());

        return new SignupRespDto(savedMember);
    }

}

