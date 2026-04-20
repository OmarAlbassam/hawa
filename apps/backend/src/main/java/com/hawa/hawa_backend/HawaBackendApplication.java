package com.hawa.hawa_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HawaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(HawaBackendApplication.class, args);
	}

}
