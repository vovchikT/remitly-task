package com.example.remitlytask.dto;

import java.util.List;

public record AuditLogResponse(List<LogEntryResponse> log) {
}
