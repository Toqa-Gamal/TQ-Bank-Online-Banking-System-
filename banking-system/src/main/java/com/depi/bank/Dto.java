package com.depi.bank;

import java.math.BigDecimal;

public class Dto {
    public record Register(String fullName, String email, String phone, String dateOfBirth,
                           String password, String confirmPassword) {}

    public record Login(String identifier, String password) {}

    public record OpenAccount(String accountType, BigDecimal openingBalance) {}

    public record Amount(BigDecimal amount) {}

    public record Transfer(Long fromAccountId, String toAccountNumber, BigDecimal amount, String note) {}

    public record Reason(String reason) {}
}
