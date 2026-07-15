package com.uniremington.api.tramita;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Scheduling habilitado para el barrido del throttling (T044, JD3-002)
@EnableScheduling
@SpringBootApplication
public class TramitaApplication {

	public static void main(String[] args) {
		SpringApplication.run(TramitaApplication.class, args);
	}

}
