package com.uniremington.api.tramita;

import org.springframework.boot.SpringApplication;

public class TestTramitaApplication {

	public static void main(String[] args) {
		SpringApplication.from(TramitaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
