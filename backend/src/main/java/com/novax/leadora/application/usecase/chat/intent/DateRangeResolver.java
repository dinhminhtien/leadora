package com.novax.leadora.application.usecase.chat.intent;

import com.novax.leadora.application.usecase.chat.time.ChatClock;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the period a question is asking about — "lead tạo hôm nay", "doanh số tháng trước".
 *
 * <p><b>Why rule-based here, when rules are the weak part of {@link IntentClassifier}.</b> The two
 * problems look alike and are not. Intent is open-ended: there is no finite list of ways to phrase
 * a business question, which is why that classifier keeps growing a keyword at a time. The way a
 * period is named is a <em>closed</em> set — a dozen anchors, plus numbers and dates — so a rule
 * covers it completely and provably, runs in microseconds, and costs no tokens. Handing this to the
 * model instead would mean an extra round trip before context can even be gathered, paid on every
 * turn to re-derive something {@code java.time} already knows.
 *
 * <p>The model still does the part it is good at: {@link ChatClock#promptBlock()} tells it today's
 * date so it can read timestamps and explain the period it was given. What it never has to do is
 * calendar arithmetic.
 *
 * <p>Anything not recognised resolves to {@link ChatDateRange#allTime()} — no filter, the exact
 * behaviour that existed before this class. A missed phrase therefore degrades to the old answer
 * rather than to a wrong one.
 */
@Component
@RequiredArgsConstructor
public class DateRangeResolver {

    /** {@code 2026-08-09}. Two of them in one message are read as a span. */
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");

    /** {@code 09/08/2026} — the everyday Vietnamese written form, day first. */
    private static final Pattern DMY_DATE = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");

    /** "7 ngày qua", "30 ngày gần đây", "last 14 days". */
    private static final Pattern LAST_N_DAYS = Pattern.compile(
            "\\b(\\d{1,3}) ngay (qua|gan day|vua qua|truoc)\\b|\\blast (\\d{1,3}) days?\\b");

    /** "3 tháng qua", "last 6 months" — checked before the bare "thang qua" anchor. */
    private static final Pattern LAST_N_MONTHS = Pattern.compile(
            "\\b(\\d{1,2}) thang (qua|gan day|vua qua|truoc)\\b|\\blast (\\d{1,2}) months?\\b");

    /**
     * "2 tuần qua", "last 3 weeks".
     *
     * <p>Must be checked before the anchor phrases. Without it, "2 tuần qua" still matched the bare
     * "tuan qua" anchor and quietly answered for seven days instead of fourteen — the worst kind of
     * miss, since a period that is merely unrecognised falls back to no filter and says so, while
     * one recognised as the wrong period is reported confidently.
     */
    private static final Pattern LAST_N_WEEKS = Pattern.compile(
            "\\b(\\d{1,2}) tuan (qua|gan day|vua qua|truoc)\\b|\\blast (\\d{1,2}) weeks?\\b");

    /**
     * "tháng 7", "tháng 12/2026". The negative lookahead keeps it off "thắng 7 deal": stripped of
     * diacritics "thắng" (won) and "tháng" (month) are the same six letters, so without it a
     * question about won deals would be silently narrowed to July.
     */
    private static final Pattern MONTH_OF_YEAR = Pattern.compile(
            "\\bthang (\\d{1,2})(?:/(\\d{4}))?(?!\\s*\\d)"
                    + "(?!\\s*(deal|lead|khach|booking|bao gia|quotation|task|cong viec|hop dong"
                    // Vietnamese counts with a classifier after the number, so "thắng 5 vụ" and
                    // "thắng 2 lần" also collide with "tháng 5" / "tháng 2". The noun list caught
                    // the objects but not the counters, and the miss is the expensive kind: the
                    // whole answer is narrowed to a month and reported as such.
                    + "|vu|lan|cai|don|ca|chuyen|suat|thuong vu|giao dich|co hoi|don hang"
                    + "|phi vu|hop dong nao|keo))");

    /**
     * Phrases that name one of {@link ChatClock#anchors()}. Insertion-ordered, most specific first,
     * so "3 tháng qua" is consumed by the regex above and "tháng qua" alone still means 30 days.
     */
    private static final Map<String, List<String>> ANCHOR_PHRASES = new LinkedHashMap<>();

    static {
        ANCHOR_PHRASES.put("today", List.of("hom nay", "ngay hom nay", "trong ngay hom nay",
                "hnay", "today", "so far today"));
        ANCHOR_PHRASES.put("yesterday", List.of("hom qua", "ngay hom qua", "yesterday"));
        ANCHOR_PHRASES.put("last_week", List.of("tuan truoc", "tuan roi", "tuan vua roi", "last week"));
        ANCHOR_PHRASES.put("this_week", List.of("tuan nay", "trong tuan nay", "trong tuan", "this week"));
        ANCHOR_PHRASES.put("last_7_days", List.of("tuan qua", "7 ngay", "last 7 days", "past week"));
        ANCHOR_PHRASES.put("last_month", List.of("thang truoc", "thang roi", "thang vua roi", "last month"));
        ANCHOR_PHRASES.put("this_month", List.of("thang nay", "trong thang nay", "trong thang", "this month"));
        ANCHOR_PHRASES.put("last_30_days", List.of("thang qua", "30 ngay", "last 30 days", "past month"));
        ANCHOR_PHRASES.put("last_quarter", List.of("quy truoc", "quy roi", "last quarter"));
        ANCHOR_PHRASES.put("this_quarter", List.of("quy nay", "trong quy nay", "trong quy", "this quarter"));
        ANCHOR_PHRASES.put("last_year", List.of("nam ngoai", "nam truoc", "nam roi", "last year"));
        ANCHOR_PHRASES.put("this_year", List.of("nam nay", "trong nam nay", "trong nam", "this year",
                "year to date", "ytd"));
    }

    private final ChatClock clock;

    /**
     * The period named by this message alone, or {@link ChatDateRange#allTime()} when it names none.
     */
    public ChatDateRange detect(String rawMessage) {
        String text = IntentClassifier.normalize(rawMessage);

        ChatDateRange explicit = explicitDates(text);
        if (explicit != null) {
            return explicit;
        }

        Matcher months = LAST_N_MONTHS.matcher(text);
        if (months.find()) {
            int n = intOf(months.group(1), months.group(3));
            LocalDate today = clock.today();
            return new ChatDateRange(today.minusMonths(Math.max(1, n)), today,
                    "the last " + Math.max(1, n) + " months");
        }

        Matcher weeks = LAST_N_WEEKS.matcher(text);
        if (weeks.find()) {
            int n = Math.max(1, intOf(weeks.group(1), weeks.group(3)));
            ChatDateRange span = clock.lastDays(n * 7);
            return new ChatDateRange(span.from(), span.to(), "the last " + n + " weeks");
        }

        Matcher days = LAST_N_DAYS.matcher(text);
        if (days.find()) {
            return clock.lastDays(intOf(days.group(1), days.group(3)));
        }

        Matcher month = MONTH_OF_YEAR.matcher(text);
        if (month.find()) {
            int m = Integer.parseInt(month.group(1));
            if (m >= 1 && m <= 12) {
                int year = month.group(2) != null
                        ? Integer.parseInt(month.group(2))
                        : clock.today().getYear();
                return clock.month(year, m);
            }
        }

        Map<String, ChatDateRange> anchors = clock.anchors();
        for (Map.Entry<String, List<String>> entry : ANCHOR_PHRASES.entrySet()) {
            for (String phrase : entry.getValue()) {
                if (mentions(text, phrase)) {
                    return anchors.get(entry.getKey());
                }
            }
        }
        return ChatDateRange.allTime();
    }

    /**
     * The period for this turn, falling back to the rest of the conversation.
     *
     * <p>Follow-ups drop the period the same way they drop the subject: "ok, liệt kê chi tiết" after
     * "lead tạo hôm nay" still means today, and re-resolving it in isolation would quietly widen the
     * answer to all time while the user believed they were still looking at today. Intent, language
     * and subject areas are all inherited this way already; a period that did not would disagree
     * with them.
     *
     * @param priorUserMessages earlier USER turns of this session, oldest first (nullable)
     */
    public ChatDateRange resolve(String current, List<String> priorUserMessages) {
        ChatDateRange here = detect(current);
        if (!here.isAllTime()) {
            return here;
        }
        // An explicit "all of it" has to be able to undo an inherited period, or the inheritance
        // is a one-way door: ask "lead hôm nay", then "tổng số lead từ trước đến nay", and the
        // second question silently keeps today's window while the Period line instructs the model
        // to present the figure as covering it. The user is shown today's count as the all-time
        // total, stated confidently. Unlike the areas this inheritance mirrors, a wrong period
        // produces a wrong number rather than a few extra rows.
        if (saysAllTime(IntentClassifier.normalize(current))) {
            return ChatDateRange.allTime();
        }
        if (priorUserMessages != null) {
            for (int i = priorUserMessages.size() - 1; i >= 0; i--) {
                ChatDateRange prior = detect(priorUserMessages.get(i));
                if (!prior.isAllTime()) {
                    return prior;
                }
            }
        }
        return ChatDateRange.allTime();
    }

    /**
     * Phrases that ask for everything, so an inherited period must be dropped rather than kept.
     *
     * <p>Only consulted once the current turn has been found to name no period of its own, so
     * "tổng số lead hôm nay" is still today — the explicit period in the same sentence wins.
     */
    private static final List<String> ALL_TIME_PHRASES = List.of(
            "tu truoc den nay", "tu truoc toi nay", "tu xua den nay", "tu dau den nay",
            "tat ca cac thoi diem", "moi thoi diem", "khong gioi han thoi gian",
            "bo loc ngay", "bo gioi han ngay", "khong loc ngay", "khong theo ngay",
            "toan bo thoi gian", "ca nam nay lan nam truoc",
            "all time", "of all time", "all-time", "no date filter", "without date filter",
            "overall total", "grand total", "since the beginning", "ever");

    /** True when the message explicitly asks to drop any period. */
    private static boolean saysAllTime(String normalized) {
        for (String phrase : ALL_TIME_PHRASES) {
            if (mentions(normalized, phrase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code phrase} appears as a whole phrase, bounded by anything that is not a letter
     * or digit.
     *
     * <p>Matching on a literal surrounding space, as this once did, is defeated by ordinary
     * punctuation: "Hôm nay, có bao nhiêu lead?" normalises to a phrase followed by a comma and
     * did not match, so the question silently fell back to no filter. A comma after a leading time
     * phrase is common enough in Vietnamese to hit regularly. The boundary check still refuses a
     * match inside a longer word, which is what the spaces were there for.
     */
    private static boolean mentions(String text, String phrase) {
        int from = 0;
        while ((from = text.indexOf(phrase, from)) >= 0) {
            int end = from + phrase.length();
            boolean leftFree = from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1));
            boolean rightFree = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (leftFree && rightFree) {
                return true;
            }
            from = end;
        }
        return false;
    }

    /** One written date means that day; two mean the span between them. */
    private ChatDateRange explicitDates(String text) {
        List<LocalDate> found = new ArrayList<>();
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find() && found.size() < 2) {
            addDate(found, Integer.parseInt(iso.group(1)),
                    Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
        }
        Matcher dmy = DMY_DATE.matcher(text);
        while (dmy.find() && found.size() < 2) {
            addDate(found, Integer.parseInt(dmy.group(3)),
                    Integer.parseInt(dmy.group(2)), Integer.parseInt(dmy.group(1)));
        }
        if (found.isEmpty()) {
            return null;
        }
        return found.size() == 1 ? clock.day(found.get(0)) : clock.between(found.get(0), found.get(1));
    }

    /** Silently skips an impossible date (31/02) rather than failing the whole turn. */
    private static void addDate(List<LocalDate> out, int year, int month, int day) {
        try {
            out.add(LocalDate.of(year, month, day));
        } catch (DateTimeException ignored) {
            // Not a real date — treat the message as naming no period at all.
        }
    }

    /** The two alternations of each pattern put the number in one group or the other. */
    private static int intOf(String a, String b) {
        return Integer.parseInt(a != null ? a : b);
    }
}
