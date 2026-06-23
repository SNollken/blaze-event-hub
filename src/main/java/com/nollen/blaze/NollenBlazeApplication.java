package com.nollen.blaze;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NollenBlazeApplication {

	public static void main(String[] args) {
		SpringApplication.run(NollenBlazeApplication.class, args);
	}

}
