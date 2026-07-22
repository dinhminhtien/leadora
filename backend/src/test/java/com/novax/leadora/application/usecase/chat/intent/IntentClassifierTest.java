package com.novax.leadora.application.usecase.chat.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour lock for the rule-based guardrail and router.
 *
 * <p>{@link IntentClassifier} is ~300 lines of hand-tuned keyword heuristics, so any change to a
 * word list can silently re-route or unblock a whole class of questions. These cases pin the
 * behaviour that matters: the read-only guardrail (BR-35), the data-scope routing (BR-36), the
 * meta-conversation escape hatch, and the language resolution used to pick the reply language.
 */
class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    /** Stands in for "the conversation already has a prior assistant turn". */
    private static final String PRIOR = ChatIntent.ASSIGNED_DATA.name();

    @Nested
    @DisplayName("BR-35 read-only guardrail")
    class MutationGuardrail {

        @ParameterizedTest(name = "blocks: {0}")
        @ValueSource(strings = {
                "xóa lead này",
                "xoa het lead di",
                "hãy tạo một lead mới cho tôi",
                "giúp tôi cập nhật deal ACME",
                "duyệt báo giá số 12 giúp mình",
                "gán lead này cho Nguyễn Văn A",
                "delete this lead",
                "please create a new deal",
                "can you approve the quotation",
        })
        void blocksImperativeMutations(String message) {
            IntentResult result = classifier.classify(message, PRIOR);
            assertThat(result.blocked()).isTrue();
            assertThat(result.intent()).isEqualTo(ChatIntent.MUTATION_BLOCKED);
            assertThat(result.blockMessage()).isNotBlank();
        }

        /**
         * A question mark no longer proves a read intent — politeness must not smuggle a command
         * past the guardrail. This was the "xóa hết lead giúp tôi được không?" bypass.
         */
        @ParameterizedTest(name = "still blocks despite '?': {0}")
        @ValueSource(strings = {
                "xóa hết lead giúp tôi được không?",
                "hãy tạo booking mới cho tôi nhé?",
                "could you please delete this deal?",
        })
        void questionMarkDoesNotUnblockACommand(String message) {
            assertThat(classifier.classify(message, PRIOR).blocked()).isTrue();
        }

        /**
         * The expensive failure mode: refusing a legitimate question that merely reuses a verb.
         * These must all pass through.
         */
        @ParameterizedTest(name = "allows: {0}")
        @ValueSource(strings = {
                "lead nào được tạo cuối cùng?",
                "ai đã xóa lead ACME?",
                "deal nào được cập nhật gần đây nhất",
                "có bao nhiêu báo giá đã được duyệt",
                "cho tôi biết task nào đã hủy",
                "which lead was created last?",
                "how many deals were approved this month",
        })
        void doesNotBlockQuestionsThatReuseMutationVerbs(String message) {
            assertThat(classifier.classify(message, PRIOR).blocked()).isFalse();
        }

        @Test
        @DisplayName("a mutation wrapped in a meta request is still blocked")
        void mutationBeatsMeta() {
            IntentResult result = classifier.classify("xóa hết lead rồi dịch sang tiếng Việt", PRIOR);
            assertThat(result.intent()).isEqualTo(ChatIntent.MUTATION_BLOCKED);
        }
    }

    @Nested
    @DisplayName("Meta-conversation requests")
    class MetaConversation {

        @ParameterizedTest(name = "meta: {0}")
        @ValueSource(strings = {
                "dịch sang tiếng việt cho tôi",
                "dich sang tieng viet",
                "dịch lại giúp mình",
                "tóm tắt lại ngắn gọn",
                "trình bày lại cho dễ đọc",
                "giải thích rõ hơn được không",
                "translate that to Vietnamese",
                "rephrase that please",
                "explain that in more detail",
        })
        void classifiesAsMetaWhenAPriorTurnExists(String message) {
            IntentResult result = classifier.classify(message, PRIOR);
            assertThat(result.blocked()).isFalse();
            assertThat(result.intent()).isEqualTo(ChatIntent.META_CONVERSATION);
        }

        /** On the very first turn there is nothing to translate — the old behaviour still applies. */
        @Test
        @DisplayName("not meta on the first turn (no prior intent)")
        void notMetaWithoutHistory() {
            IntentResult result = classifier.classify("dịch sang tiếng việt cho tôi", null);
            assertThat(result.intent()).isNotEqualTo(ChatIntent.META_CONVERSATION);
        }

        /** Chained meta requests must keep working ("dịch lại lần nữa"). */
        @Test
        @DisplayName("meta can follow meta")
        void metaAfterMeta() {
            IntentResult result =
                    classifier.classify("tóm tắt lại giúp mình", ChatIntent.META_CONVERSATION.name());
            assertThat(result.intent()).isEqualTo(ChatIntent.META_CONVERSATION);
        }
    }

    @Nested
    @DisplayName("BR-36 data-scope routing")
    class Routing {

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
                "'cho tôi xem lead của tôi',            ASSIGNED_DATA",
                "'deal của mình đang ở giai đoạn nào',  ASSIGNED_DATA",
                "'show my leads',                       ASSIGNED_DATA",
                "'task nào đang quá hạn',               ASSIGNED_DATA",
                "'tổng hợp doanh số cả team',           TEAM_SUMMARY",
                "'xếp hạng nhân viên theo doanh thu',   TEAM_SUMMARY",
                "'so sánh hiệu suất giữa các bạn',      TEAM_SUMMARY",
                "'nội quy công ty quy định thế nào',    DOC_QUERY",
                "'quy trình đặt phòng ra sao',          DOC_QUERY",
        })
        void routesToTheRightSource(String message, ChatIntent expected) {
            assertThat(classifier.classify(message, null).intent()).isEqualTo(expected);
        }

        /**
         * An explicit possessive outranks the team keywords. "top " is both a ranking word and a
         * team word, so this used to be answered with the whole team's data.
         */
        @ParameterizedTest(name = "possessive wins: {0}")
        @ValueSource(strings = {
                "top 5 deal của tôi",
                "deal nhiều nhất của mình là gì",
                "my top deals",
        })
        void possessiveBeatsTeamKeywords(String message) {
            assertThat(classifier.classify(message, null).intent()).isEqualTo(ChatIntent.ASSIGNED_DATA);
        }

        @Test
        @DisplayName("a genuine team question without a possessive still routes to TEAM_SUMMARY")
        void teamQuestionWithoutPossessive() {
            assertThat(classifier.classify("top 5 nhân viên bán tốt nhất", null).intent())
                    .isEqualTo(ChatIntent.TEAM_SUMMARY);
        }
    }

    @Nested
    @DisplayName("Off-topic and greetings")
    class OffTopic {

        @ParameterizedTest(name = "off-topic: {0}")
        @ValueSource(strings = {
                "giải phương trình bậc hai giúp tôi",
                "viết code python đọc file csv",
                "thời tiết hôm nay thế nào",
                "kể một câu chuyện cười",
        })
        void refusesOffTopic(String message) {
            IntentResult result = classifier.classify(message, PRIOR);
            assertThat(result.blocked()).isTrue();
            assertThat(result.intent()).isEqualTo(ChatIntent.OFF_TOPIC);
        }

        @ParameterizedTest(name = "greeting: {0}")
        @ValueSource(strings = {"xin chào", "hello", "bạn có thể làm gì", "cảm ơn"})
        void greetingsAreAllowed(String message) {
            IntentResult result = classifier.classify(message, null);
            assertThat(result.blocked()).isFalse();
            assertThat(result.intent()).isEqualTo(ChatIntent.GENERAL_BUSINESS);
        }

        @Test
        @DisplayName("an ambiguous follow-up inherits the prior data scope")
        void ambiguousFollowUpInheritsScope() {
            assertThat(classifier.classify("còn ai nữa không", ChatIntent.TEAM_SUMMARY.name()).intent())
                    .isEqualTo(ChatIntent.TEAM_SUMMARY);
        }
    }

    @Nested
    @DisplayName("Reply language resolution")
    class Language {

        @ParameterizedTest(name = "Vietnamese: {0}")
        @ValueSource(strings = {
                "cho tôi xem lead của tôi",
                "cho toi xem lead cua toi",
                "deal nào đang mở",
        })
        void detectsVietnamese(String message) {
            assertThat(IntentClassifier.resolveVietnamese(message, List.of())).isTrue();
        }

        @ParameterizedTest(name = "English: {0}")
        @ValueSource(strings = {
                "show my lead",
                "what is the total revenue",
                "list all overdue tasks",
        })
        void detectsEnglish(String message) {
            assertThat(IntentClassifier.resolveVietnamese(message, List.of())).isFalse();
        }

        @Test
        @DisplayName("a short turn inherits the language of the last decisive turn")
        void shortTurnInheritsSessionLanguage() {
            List<String> englishSession = List.of("show my leads", "and the deals");
            assertThat(IntentClassifier.resolveVietnamese("more", englishSession)).isFalse();

            List<String> vietnameseSession = List.of("cho tôi xem lead của tôi");
            assertThat(IntentClassifier.resolveVietnamese("ok", vietnameseSession)).isTrue();
        }

        @Test
        @DisplayName("defaults to Vietnamese when nothing in the session is decisive")
        void defaultsToVietnamese() {
            assertThat(IntentClassifier.resolveVietnamese("ok", List.of())).isTrue();
            assertThat(IntentClassifier.resolveVietnamese("ok", List.of("hmm"))).isTrue();
        }

        /**
         * The scenario that motivated this: an English answer, then a Vietnamese request to
         * translate it — the switch must take effect on the turn that asks for it.
         */
        @Test
        @DisplayName("switches language on an explicit request")
        void switchesLanguageMidConversation() {
            List<String> englishSession = List.of("show my lead");
            assertThat(IntentClassifier.resolveVietnamese("dịch sang tiếng việt cho tôi", englishSession))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Guardrail messages follow the resolved language")
    class GuardrailLanguage {

        @Test
        void refusalIsVietnameseForAVietnameseTurn() {
            IntentResult result = classifier.classify("hãy xóa lead này", PRIOR, true);
            assertThat(result.blockMessage()).contains("chỉ đọc");
        }

        @Test
        void refusalIsEnglishForAnEnglishTurn() {
            IntentResult result = classifier.classify("please delete this lead", PRIOR, false);
            assertThat(result.blockMessage()).contains("read-only");
        }
    }
}
