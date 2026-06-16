package com.personalrouter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PersonalRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonalRouterApplication.class, args);
    }
}
