package com.novax.leadora.application.usecase.chat.intent;

import com.novax.leadora.application.usecase.chat.GuardrailMessages;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;

/**
 * Rule-based classifier — step [1] of the hybrid chat pipeline.
 *
 * <p>It runs entirely in the backend so the read-only guarantee (BR-35) and the
 * "business questions only" policy do not depend on the local LLM behaving. It is the
 * first line of defence; the system prompt in the LLM layer is the backstop.
 *
 * <p>Matching is done on a diacritic-stripped, lower-cased form so Vietnamese typed
 * with or without accents (e.g. "xóa" / "xoa") is treated the same.
 */
@Component
public class IntentClassifier {

    // Always-refuse verbs: destructive or irreversible, blocked regardless of object.
    private static final List<String> HARD_MUTATION_VERBS = List.of(
            "xoa", "delete", "remove", "drop", "huy don", "huy bo", "xoa bo");

    // Action verbs that mutate data — blocked when aimed at a CRM object.
    private static final List<String> SOFT_MUTATION_VERBS = List.of(
            "tao ", "them ", "create", "add ", "insert",
            "gui ", "send", "duyet", "phe duyet", "approve",
            "tu choi", "reject", "xac nhan", "confirm",
            "gan", "assign", "reassign", "chuyen giao",
            "chinh sua", "cap nhat", "update", "sua ", "edit", "modify",
            "thay doi", "doi ", "huy", "cancel", "luu lai");

    // Phrases that signal the user wants to READ/see data (defuses ambiguous verbs like "cap nhat").
    private static final List<String> READ_INTENT = List.of(
            "cho toi biet", "cho toi", "cho minh", "liet ke", "danh sach", "xem", "thong tin",
            "tinh hinh", "bao nhieu", "co nhung", "tom tat", "tong hop", "bao cao", "thong ke",
            "how many", "list", "show", "what", "which", "summary", "report", "tell me", "tinh trang");

    private static final List<String> CRM_OBJECTS = List.of(
            "lead", "khach hang", "khach", "deal", "co hoi", "giao dich", "thuong vu",
            "task", "cong viec", "nhiem vu", "bao gia", "quotation", "booking", "dat phong",
            "dat cho", "thanh toan", "payment", "feedback", "phan hoi", "handover", "ban giao",
            "reminder", "nhac", "sla", "nguoi dung", "tai khoan", "ho so");

    private static final List<String> BUSINESS_KEYWORDS = List.of(
            // CRM objects are business keywords too
            "lead", "khach", "deal", "co hoi", "giao dich", "task", "cong viec", "bao gia",
            "quotation", "booking", "dat phong", "thanh toan", "payment", "feedback", "handover",
            "ban giao", "reminder", "sla",
            // sales / pipeline vocabulary
            "doanh so", "doanh thu", "revenue", "sales", "ban hang", "pipeline", "chot", "close",
            "won", "lost", "thang", "thua", "qua han", "overdue", "ty le", "conversion", "chuyen doi",
            "muc tieu", "target", "hieu suat", "performance", "tien do",
            // company knowledge / docs
            "cong ty", "noi quy", "quy dinh", "chinh sach", "policy", "tai lieu", "document",
            "huong dan", "quy trinh", "process", "so tay", "handbook");

    private static final List<String> TEAM_KEYWORDS = List.of(
            "team", "nhom", "doi", "ca team", "ca nhom", "toan team", "toan bo nhan vien",
            "moi nguoi", "everyone", "tat ca nhan vien", "xep hang", "ranking", "top ",
            "nhieu nhat", "cao nhat", "trung binh", "average", "tung nhan vien", "moi nhan vien",
            "theo nhan vien", "per rep", "so sanh", "tong cong ty", "toan cong ty");

    private static final List<String> ASSIGNED_KEYWORDS = List.of(
            "cua toi", "cua minh", "toi co", "minh co", "toi dang", "duoc giao cho toi",
            "assigned to me", "my ", "i have", "toi phu trach", "minh phu trach", "cua em");

    private static final List<String> DOC_KEYWORDS = List.of(
            "noi quy", "quy dinh", "chinh sach", "policy", "tai lieu", "document", "huong dan",
            "quy trinh", "so tay", "handbook", "dieu khoan", "theo cong ty", "quy che");

    // Clear off-topic signals (only used when NO business keyword is present).
    private static final List<String> OFF_TOPIC_SIGNALS = List.of(
            "tinh giup", "tinh ho", "giai phuong trinh", "phuong trinh", "bai toan", "phep tinh",
            "dao ham", "tich phan", "can bac", "math", "calculate", "solve",
            "viet code", "lap trinh", "thuat toan", "code giup", "debug",
            "dich giup", "translate", "thoi tiet", "weather", "nau an", "cong thuc nau",
            "lam tho", "ke chuyen", "ke mot", "joke", "cuoi", "bong da", "ty so", "game",
            "ai la ai trong phim", "nghia la gi tieng");

    private static final List<String> GREETINGS = List.of(
            "xin chao", "chao", "hello", "hi ", "hey", "ban la ai", "ban co the lam gi",
            "giup gi", "help", "huong dan su dung", "ban giup");

    public IntentResult classify(String rawMessage) {
        String text = normalize(rawMessage);

        // [1] Read-only guardrail (BR-35) — highest priority.
        if (isMutation(text)) {
            return IntentResult.blocked(ChatIntent.MUTATION_BLOCKED, GuardrailMessages.MUTATION_REFUSAL);
        }

        boolean hasBusiness = containsAny(text, BUSINESS_KEYWORDS);

        // [2] Off-topic guardrail — only when there is no business signal at all.
        if (!hasBusiness) {
            if (containsAny(text, GREETINGS)) {
                return IntentResult.of(ChatIntent.GENERAL_BUSINESS);
            }
            if (containsAny(text, OFF_TOPIC_SIGNALS)) {
                return IntentResult.blocked(ChatIntent.OFF_TOPIC, GuardrailMessages.OFF_TOPIC_REFUSAL);
            }
            // Ambiguous & non-business → let the LLM answer under a strict system prompt.
            return IntentResult.of(ChatIntent.GENERAL_BUSINESS);
        }

        // [3] Route business questions to the right data source.
        if (containsAny(text, DOC_KEYWORDS)) {
            return IntentResult.of(ChatIntent.DOC_QUERY);
        }
        if (containsAny(text, TEAM_KEYWORDS)) {
            return IntentResult.of(ChatIntent.TEAM_SUMMARY);
        }
        if (containsAny(text, ASSIGNED_KEYWORDS) || containsAny(text, CRM_OBJECTS)) {
            return IntentResult.of(ChatIntent.ASSIGNED_DATA);
        }
        return IntentResult.of(ChatIntent.GENERAL_BUSINESS);
    }

    private boolean isMutation(String text) {
        if (containsAny(text, HARD_MUTATION_VERBS)) {
            return true;
        }
        for (String verb : SOFT_MUTATION_VERBS) {
            if (text.contains(verb) && containsAny(text, CRM_OBJECTS)) {
                // "cap nhat / update / sua / doi / change / huy" are ambiguous with read requests
                // ("cập nhật cho tôi tình hình deal"). If the turn clearly asks to view data, don't block.
                boolean ambiguousVerb = verb.startsWith("cap nhat") || verb.startsWith("update")
                        || verb.startsWith("sua") || verb.startsWith("doi") || verb.startsWith("thay doi")
                        || verb.startsWith("huy") || verb.startsWith("cancel") || verb.startsWith("modify")
                        || verb.startsWith("edit");
                if (ambiguousVerb && containsAny(text, READ_INTENT)) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, List<String> needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /** Lower-case, strip Vietnamese diacritics, collapse whitespace. */
    static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String lower = input.toLowerCase();
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd');
        return " " + decomposed.replaceAll("\\s+", " ").trim() + " ";
    }
}
