package nl.banksim.api.simulatie;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** De echte klok; tests geven de SimulationClock een vaste (ADR 0013). */
@Configuration
class KlokConfig {

    @Bean
    SimulationClock simulationClock(SimulatiedatumRepository simulatiedatum) {
        return new SimulationClock(Clock.system(SimulationClock.BANK), simulatiedatum);
    }
}
