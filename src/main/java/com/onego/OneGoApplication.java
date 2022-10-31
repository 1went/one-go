package com.onego;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.onego.mapper")
@SpringBootApplication
public class OneGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneGoApplication.class, args);
    }

}
