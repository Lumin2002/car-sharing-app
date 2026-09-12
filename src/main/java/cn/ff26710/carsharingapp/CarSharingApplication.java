package cn.ff26710.carsharingapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("cn.ff26710.carsharingapp.mapper")
@EnableScheduling
public class CarSharingApplication {
    public static void main(String[] args) {
        SpringApplication.run(CarSharingApplication.class, args);
    }
}