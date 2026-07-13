package com.pdfsuite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PdfSuiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfSuiteApplication.class, args);
    }
}
