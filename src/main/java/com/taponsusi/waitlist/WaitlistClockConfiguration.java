package com.taponsusi.waitlist;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class WaitlistClockConfiguration {

    @Bean
    public Clock waitlistClock() {
        return Clock.systemUTC();
    }
}
