package com.novax.leadora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LeadoraApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeadoraApplication.class, args);
	}

}
