package com.sky.dto;


import lombok.Data;

@Data
public class PasswordDTO {
    private String oldPassword;
    private String newPassword;
}
