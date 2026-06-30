package com.example.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DebugLogger {

    @Value("${app.debug:false}")
    private boolean debug;

    public DebugLogger() {
        this.debug = false;
    }

    public DebugLogger(boolean debug) {
        this.debug = debug;
    }

    public void log(String message) {
        if (debug) {
            System.out.println(message);
        }
    }

    public void error(String message) {
        if (debug) {
            System.err.println(message);
        }
    }

    public boolean isDebugEnabled() {
        return debug;
    }
}
