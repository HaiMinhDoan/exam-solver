package com.examsolver.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HumanAnswerRequest {

    /** Đáp án giáo viên nhập: "A", "A,C", text tự luận... */
    @NotBlank(message = "Đáp án không được để trống")
    private String answer;
}