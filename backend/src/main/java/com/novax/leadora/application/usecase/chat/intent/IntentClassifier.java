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
            "xoa", "delete", "remove", "drop", "huy don", "huy bo", "xoa bo",
            "xoa het", "xoa tat ca", "purge", "wipe", "destroy", "truncate");

    // Action verbs that mutate data — blocked when aimed at a CRM object.
    private static final List<String> SOFT_MUTATION_VERBS = List.of(
            "tao ", "tao moi", "khoi tao", "them ", "create", "add ", "insert", "nhap ", "import",
            "gui ", "send", "duyet", "phe duyet", "approve",
            "tu choi", "reject", "xac nhan", "confirm", "chap nhan", "accept",
            "gan", "assign", "reassign", "chuyen giao", "phan cong", "ban giao cho",
            "chinh sua", "cap nhat", "update", "sua ", "edit", "modify", "ghi de", "ghi lai",
            "thay doi", "doi ", "huy", "cancel", "luu lai", "luu ", "save",
            "dat lai", "reset", "khoa ", "lock", "mo khoa", "unlock",
            "kich hoat", "activate", "vo hieu hoa", "deactivate",
            "gia han", "extend", "keo ", "move ", "chuyen sang", "dong deal", "chot deal");

    // Imperative markers — a request only counts as a mutation command when phrased like an order.
    private static final List<String> COMMAND_MARKERS = List.of(
            "hay ", "giup toi", "giup minh", "giup em", "giup ", "lam on", "vui long",
            "lam giup", "thuc hien", "tien hanh", "ho toi", "gium toi", "gium minh",
            "please", "can you", "could you", "would you", "i want you to", "let's");

    // Question / read markers — if present, the verb is part of a question, not a command
    // ("lead nào được tạo cuối cùng?", "ai đã xóa..."). Deliberately excludes the bare "cho toi"
    // because that also appears in real commands ("xóa ... cho tôi").
    private static final List<String> QUESTION_READ_MARKERS = List.of(
            "?", "cho toi biet", "cho toi xem", "cho minh biet", "cho biet", "liet ke", "danh sach",
            "bao nhieu", "co bao nhieu", "dem ", "la ai", "la gi", "nao ", "khi nao", "the nao",
            "ngay tao", "luc nao", "o dau", "tai sao", "vi sao", "co phai", "phai khong", "dung khong",
            "ai tao", "ai them", "nguoi tao", "tinh hinh", "tom tat", "tong hop", "thong ke", "bao cao",
            "chi tiet", "thong tin", "tra cuu", "tim ", "tim kiem", "hien thi", "co nhung", "gom ",
            "how many", "how much", "list", "show", "what", "which", "who", "when", "where", "why",
            "summary", "report", "tell me", "are there", "do i have", "find", "search", "display");

    // Verbs that, when a message STARTS with them, indicate an imperative command.
    private static final List<String> START_VERBS = List.of(
            "xoa", "delete", "remove", "drop", "huy", "purge",
            "tao ", "them ", "create", "add ", "insert", "nhap ", "import",
            "sua ", "chinh sua", "cap nhat", "update", "edit", "modify",
            "gui ", "send", "duyet", "phe duyet", "approve", "tu choi", "reject",
            "xac nhan", "confirm", "gan ", "assign", "phan cong", "doi ", "thay doi", "change",
            "khoa ", "lock", "mo khoa", "unlock", "kich hoat", "vo hieu hoa", "reset", "dat lai");

    private static final List<String> CRM_OBJECTS = List.of(
            "lead", "khach hang tiem nang", "khach hang", "khach", "client", "deal", "co hoi",
            "co hoi ban hang", "giao dich", "thuong vu", "opportunity", "hop dong",
            "task", "cong viec", "nhiem vu", "viec can lam", "cong viec can lam",
            "lich hen", "cuoc hen", "appointment",
            "bao gia", "quotation", "quote", "booking", "dat phong", "dat cho", "don dat phong",
            "thanh toan", "payment", "hoa don", "invoice", "tien coc", "dat coc", "deposit",
            "feedback", "phan hoi", "danh gia", "review", "handover", "ban giao",
            "reminder", "nhac", "loi nhac", "nhac nho", "sla",
            "nguoi dung", "tai khoan", "user", "account", "ho so",
            "san pham", "dich vu", "product", "service", "lien he", "contact", "nguoi lien he",
            "tuong tac", "interaction", "timeline", "dong thoi gian", "lich su tuong tac");

    private static final List<String> BUSINESS_KEYWORDS = List.of(
            // CRM objects are business keywords too
            "lead", "khach", "client", "deal", "co hoi", "giao dich", "opportunity", "hop dong",
            "task", "cong viec", "nhiem vu", "lich hen", "bao gia", "quotation", "quote",
            "booking", "dat phong", "dat cho", "thanh toan", "payment", "hoa don", "tien coc",
            "deposit", "feedback", "phan hoi", "danh gia", "handover", "ban giao", "reminder",
            "nhac", "sla", "san pham", "dich vu", "lien he", "contact", "tuong tac", "interaction",
            // sales / pipeline vocabulary
            "doanh so", "doanh thu", "revenue", "sales", "ban hang", "pipeline", "chot",
            "close", "won", "lost", "thang", "thua", "qua han", "overdue", "ty le", "conversion",
            "chuyen doi", "muc tieu", "target", "kpi", "chi tieu", "hieu suat", "performance",
            "tien do", "loi nhuan", "profit", "gia tri", "value", "tang truong", "growth",
            "cham soc", "follow up", "theo doi", "trang thai", "status", "giai doan", "stage",
            "uu tien", "priority", "han chot", "deadline", "lich su", "phan tich", "thong ke",
            "bao cao", "dashboard", "bang dieu khien",
            // company knowledge / docs
            "cong ty", "noi quy", "quy dinh", "chinh sach", "policy", "tai lieu", "document",
            "huong dan", "quy trinh", "process", "so tay", "handbook", "quy che", "bieu mau", "sop");

    private static final List<String> TEAM_KEYWORDS = List.of(
            "team", "nhom", "doi", "ca team", "ca nhom", "ca doi", "toan team", "toan doi",
            "toan bo nhan vien", "toan the", "phong ban", "department", "dong nghiep",
            "moi nguoi", "everyone", "tat ca nhan vien", "ai dang", "ai co", "nhan vien nao",
            "ban nao", "xep hang", "ranking", "top ", "best", "gioi nhat", "kem nhat",
            "nhieu nhat", "cao nhat", "thap nhat", "it nhat", "trung binh", "average",
            "tung nhan vien", "moi nhan vien", "theo nhan vien", "per rep", "so sanh", "compare",
            "tong cong ty", "toan cong ty", "tong so", "toan bo");

    private static final List<String> ASSIGNED_KEYWORDS = List.of(
            "cua toi", "cua minh", "cua em", "cua tao", "toi co", "minh co", "em co", "toi dang",
            "minh dang", "em dang", "duoc giao cho toi", "giao cho toi", "toi phu trach",
            "minh phu trach", "em phu trach", "toi quan ly", "minh quan ly", "do toi", "thuoc ve toi",
            "assigned to me", "my ", "mine", "my own", "i have", "i am handling", "i manage");

    private static final List<String> DOC_KEYWORDS = List.of(
            "noi quy", "quy dinh", "chinh sach", "policy", "tai lieu", "document", "huong dan",
            "quy trinh", "process", "so tay", "handbook", "dieu khoan", "theo cong ty", "quy che",
            "bieu mau", "mau don", "form", "tieu chuan", "standard", "sop", "cam nang", "thoa thuan",
            "faq", "cau hoi thuong gap", "huong dan su dung", "theo noi quy", "theo quy dinh",
            "theo chinh sach", "theo tai lieu");

    // Clear off-topic signals (only used when NO business keyword is present).
    private static final List<String> OFF_TOPIC_SIGNALS = List.of(
            "tinh giup", "tinh ho", "giai phuong trinh", "phuong trinh", "bai toan", "phep tinh",
            "dao ham", "tich phan", "can bac", "math", "calculate", "solve",
            "viet code", "lap trinh", "thuat toan", "code giup", "debug", "ham python", "javascript",
            "dich giup", "dich sang", "translate", "song ngu", "thoi tiet", "weather",
            "nau an", "cong thuc nau", "mon an", "recipe",
            "lam tho", "viet van", "viet bai", "sang tac", "ke chuyen", "ke mot cau chuyen",
            "joke", "ke chuyen cuoi", "bong da", "ty so", "game", "tro choi",
            "phim", "ca si", "bai hat", "lyrics", "loi bai hat", "dien vien", "tin tuc", "news",
            "chinh tri", "politics", "tu vi", "boi toan", "horoscope", "y nghia cuoc song",
            "tinh yeu", "nguoi yeu", "ai la ai trong phim", "nghia la gi tieng");

    private static final List<String> GREETINGS = List.of(
            "xin chao", "chao", "hello", "hi ", "hey", "ban la ai", "ban ten gi", "ban co the lam gi",
            "ban lam duoc gi", "giup gi", "giup duoc gi", "help", "huong dan su dung", "ban giup",
            "gioi thieu", "cam on", "thanks", "thank you", "good morning", "chao buoi");

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
    public static boolean isVietnamese(String raw) {
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
