package com.metrology.balance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BalanceSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanceSystemApplication.class, args);
    }
}
