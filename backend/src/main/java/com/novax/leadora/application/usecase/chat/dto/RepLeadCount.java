package com.novax.leadora.application.usecase.chat.dto;

/**
 * Lead count per sales rep.
 *
 * <p>Used only to build the "what you may ask instead" suggestions shown to a Manager/Admin whose
 * personal scope is empty. It must never be exposed to a role that cannot see all records (BR-36).
 */
public record RepLeadCount(String repName, Long count) {
}
