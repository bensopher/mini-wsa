package com.bensopher.miniwsa.common.validation;

import com.google.common.net.InetAddresses;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IpAddressValidator implements ConstraintValidator<IpAddress, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        // Zone IDs (fe80::1%eth0) are rejected up front: Postgres INET can't store them, and Guava would
        // look the interface up on this host while parsing.
        if (value.indexOf('%') >= 0) {
            return false;
        }
        // Guava parses literals only (never DNS, unlike InetAddress.getByName) and rejects leading-zero octets.
        return InetAddresses.isInetAddress(value);
    }
}
