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

    // Imperative markers — a request only counts as a mutation command when phrased like an order.
    private static final List<String> COMMAND_MARKERS = List.of(
            "hay ", "giup toi", "giup minh", "giup em", "lam on", "vui long",
            "please", "can you", "could you");

    // Question / read markers — if present, the verb is part of a question, not a command
    // ("lead nào được tạo cuối cùng?", "ai đã xóa..."). Deliberately excludes the bare "cho toi"
    // because that also appears in real commands ("xóa ... cho tôi").
    private static final List<String> QUESTION_READ_MARKERS = List.of(
            "?", "cho toi biet", "cho toi xem", "cho minh biet", "cho biet", "liet ke", "danh sach",
            "bao nhieu", "la ai", "la gi", "nao ", "khi nao", "the nao", "ngay tao", "luc nao",
            "ai tao", "ai them", "nguoi tao", "tinh hinh", "tom tat", "tong hop", "thong ke", "bao cao",
            "how many", "list", "show", "what", "which", "who", "when", "summary", "report", "tell me");

    // Verbs that, when a message STARTS with them, indicate an imperative command.
    private static final List<String> START_VERBS = List.of(
            "xoa", "delete", "remove", "drop", "huy",
            "tao ", "them ", "create", "add ", "insert", "sua ", "chinh sua", "cap nhat", "update",
            "edit", "modify", "gui ", "send", "duyet", "phe duyet", "approve", "tu choi", "reject",
            "xac nhan", "confirm", "gan ", "assign", "doi ", "thay doi", "change");

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
            "lam tho", "ke chuyen", "ke mot cau chuyen", "joke", "bong da", "ty so", "game",
            "ai la ai trong phim", "nghia la gi tieng");

    private static final List<String> GREETINGS = List.of(
            "xin chao", "chao", "hello", "hi ", "hey", "ban la ai", "ban co the lam gi",
            "giup gi", "help", "huong dan su dung", "ban giup");

    public IntentResult classify(String rawMessage) {
        return classify(rawMessage, null);
    }

    /**
     * @param lastIntentName the {@code intentMatched} of the previous assistant turn (nullable).
     *                       Lets ambiguous follow-ups ("ai được thêm cuối cùng?") continue the
     *                       ongoing business conversation instead of being treated as off-topic.
     */
    public IntentResult classify(String rawMessage, String lastIntentName) {
        String text = normalize(rawMessage);
        boolean vi = isVietnamese(rawMessage);

        // [1] Read-only guardrail (BR-35) — highest priority.
        if (isMutation(text)) {
            return IntentResult.blocked(ChatIntent.MUTATION_BLOCKED, GuardrailMessages.mutationRefusal(vi));
        }

        boolean hasBusiness = containsAny(text, BUSINESS_KEYWORDS);

        // [2] Off-topic / continuation handling — only when there is no business signal at all.
        if (!hasBusiness) {
            // A clear off-topic signal (math, code, weather...) is refused even mid-conversation.
            if (containsAny(text, OFF_TOPIC_SIGNALS)) {
                return IntentResult.blocked(ChatIntent.OFF_TOPIC, GuardrailMessages.offTopicRefusal(vi));
            }
            if (containsAny(text, GREETINGS)) {
                return IntentResult.of(ChatIntent.GENERAL_BUSINESS);
            }
            // Ambiguous follow-up inside a business conversation → keep the prior data scope so
            // the LLM still receives the relevant CRM/RAG context to resolve the reference.
            ChatIntent inherited = dataIntentOrNull(lastIntentName);
            if (inherited != null) {
                return IntentResult.of(inherited);
            }
            // First-turn ambiguous & non-business → let the LLM answer under a strict system prompt.
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

    /** Returns the intent to continue if {@code name} is a data/RAG intent, else null. */
    private ChatIntent dataIntentOrNull(String name) {
        if (name == null) {
            return null;
        }
        try {
            ChatIntent prev = ChatIntent.valueOf(name);
            return switch (prev) {
                case ASSIGNED_DATA, TEAM_SUMMARY, DOC_QUERY, GENERAL_BUSINESS -> prev;
                default -> null;
            };
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * A turn is a blocked mutation only when it is phrased as a COMMAND, not a question.
     *
     * <p>This is deliberately conservative: the assistant is architecturally read-only (it has no
     * tool to write data), so a missed command still gets refused by the LLM system prompt. The
     * expensive failure is the opposite — blocking a legitimate question that merely reuses a verb
     * ("lead nào được <b>tạo</b> cuối cùng?", "ai đã <b>xóa</b> lead X?"). So we require an
     * imperative phrasing and the absence of question/read markers.
     */
    private boolean isMutation(String text) {
        boolean hasHardVerb = containsAny(text, HARD_MUTATION_VERBS);
        boolean hasSoftVerbOnObject = false;
        for (String verb : SOFT_MUTATION_VERBS) {
            if (text.contains(verb) && containsAny(text, CRM_OBJECTS)) {
                hasSoftVerbOnObject = true;
                break;
            }
        }
        if (!hasHardVerb && !hasSoftVerbOnObject) {
            return false;
        }

        boolean imperative = containsAny(text, COMMAND_MARKERS) || startsWithVerb(text);
        boolean questionOrRead = containsAny(text, QUESTION_READ_MARKERS);
        return imperative && !questionOrRead;
    }

    /** True when the message begins with a mutation verb (imperative form). */
    private boolean startsWithVerb(String wrappedText) {
        for (String verb : START_VERBS) {
            if (wrappedText.startsWith(" " + verb)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Heuristic language detection for picking the canned-message language. Returns Vietnamese when
     * the raw text has Vietnamese diacritics or common Vietnamese function words; English otherwise.
     */
    static boolean isVietnamese(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String lower = raw.toLowerCase();
        if (lower.matches("(?s).*[ăâđêôơưàáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệìíỉĩịòóỏõọồốổỗộờớởỡợùúủũụừứửữựỳýỷỹỵ].*")) {
            return true;
        }
        String wrapped = " " + lower.replaceAll("\\s+", " ").trim() + " ";
        String[] viWords = {
                " toi ", " ban ", " cua ", " cho ", " xem ", " khong ", " nguoi ", " duoc ",
                " danh sach ", " giup ", " la ", " va ", " nhung ", " bao nhieu ", " cuoi cung ",
                " hay ", " gium ", " minh ", " nao ", " bao cao ", " khach "
        };
        for (String w : viWords) {
            if (wrapped.contains(w)) {
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
