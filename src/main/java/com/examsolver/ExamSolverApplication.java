package com.examsolver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExamSolverApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExamSolverApplication.class, args);
    }
}
