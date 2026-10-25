package nl.banksim.api.rente;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** ADR 0028: de nachtelijke maandafsluiting draait met de planner van Spring. */
@Configuration
@EnableScheduling
class PlanningConfig {
}
