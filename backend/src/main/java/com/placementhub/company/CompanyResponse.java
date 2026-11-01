package com.placementhub.company;

import java.time.Instant;

public record CompanyResponse(Long id, String name, String website, String description, Instant createdAt) {
}
