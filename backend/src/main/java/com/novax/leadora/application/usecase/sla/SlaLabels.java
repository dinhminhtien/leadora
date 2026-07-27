package com.novax.leadora.application.usecase.sla;

/**
 * Human-readable labels for SLA activity/entity type keys — shared across
 * warning, breach and escalation notification use cases.
 */
final class SlaLabels {

    private SlaLabels() {
    }

    static String activityLabel(String activityType) {
        return switch (activityType) {
            case "LEAD_RESPONSE" -> "Lead Response";
            case "QUOTATION_SENT" -> "Quotation Dispatch";
            case "FOLLOW_UP_TASK" -> "Follow-up Task";
            case "PAYMENT_DEPOSIT" -> "Payment Deposit";
            case "HANDOVER_SUBMISSION" -> "Handover Submission";
            case "QUOTATION_APPROVAL" -> "Quotation Approval";
            case "CUSTOMER_FEEDBACK_RESPONSE" -> "Customer Feedback Response";
            case "BOOKING_CONFIRM" -> "Booking Confirmation";
            default -> activityType;
        };
    }

    static String entityLabel(String entityType) {
        return switch (entityType) {
            case "LEAD" -> "Lead";
            case "QUOTATION" -> "Quotation";
            case "TASK" -> "Task";
            case "BOOKING" -> "Booking";
            default -> entityType;
        };
    }
}
