package com.example.userservice;

/** Thrown when the public demo already holds the maximum number of users. */
public class DemoLimitReachedException extends RuntimeException {
    public DemoLimitReachedException(int maxUsers) {
        super("The demo is full (" + maxUsers + " users). Data resets every night at 03:00 UK time.");
    }
}
