package com.web.domain.enums;

public enum BoardType {
    NOTICE("공지사항"),
    FREE("자유게시판");

    private String value;

    BoardType(String value){
        this.value = value;
    }

    public String getValue(){
        return this.value;
    }
}
