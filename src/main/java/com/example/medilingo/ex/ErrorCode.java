package com.example.medilingo.ex;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "해당 유저를 찾을 수 없습니다."),
    USER_ALREADY_EXIST(HttpStatus.ALREADY_REPORTED.value(), "이미 존재하는 유저입니다."),
    TOKEN_MISSING(HttpStatus.BAD_REQUEST.value(), "토큰이 존재하지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.BAD_REQUEST.value(), "토큰이 만료되었습니다."),
    INVALID_TOKEN_TYPE(HttpStatus.BAD_REQUEST.value(), "토큰 타입이 잘못되었습니다."),
    INVALID_TOKEN_FORMAT(HttpStatus.BAD_REQUEST.value(), "토큰 형식이 잘못되었습니다."),
    INVALID_TOKEN_SIGNATURE(HttpStatus.BAD_REQUEST.value(), "서명 토큰이 잘못되었습니다."),
    INVALID_TOKEN_PARSING(HttpStatus.BAD_REQUEST.value(), "토큰 파싱이 잘못되었습니다"),
    PAPER_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "해당 논문을 찾을 수 없습니다."),
    ANCHOR_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "해당 앵커를 찾을 수 없습니다."),
    MEMO_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "해당 메모를 찾을 수 없습니다."),
    NO_BODY(HttpStatus.NO_CONTENT.value(), "내용이 없습니다."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST.value(), "잘못된 상태입니다."),
    PINECONE_CONNECTION_FAIL(HttpStatus.NO_CONTENT.value(), "파인콘 연결에 실패했습니다."),
    RECOMMENDATION_FAIL(HttpStatus.NO_CONTENT.value(), "추천 설명에 실패했습니다."),
    STATS_NOT_FOUND(HttpStatus.NO_CONTENT.value(), "유저 통계 테이블이 존재하지 않습니다."),
    ;

    private final int status;
    private final String message;

    ErrorCode(int status, String message){
        this.status = status;
        this.message = message;
    }
}
