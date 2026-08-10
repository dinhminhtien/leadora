package com.novax.leadora.application.usecase.chat;

import com.novax.leadora.api.dto.response.ChatMessageResponse;
import com.novax.leadora.application.usecase.chat.intent.ChatIntent;
import com.novax.leadora.application.usecase.chat.intent.CrmArea;
import com.novax.leadora.application.usecase.chat.intent.DateRangeResolver;
import com.novax.leadora.application.usecase.chat.intent.IntentClassifier;
import com.novax.leadora.application.usecase.chat.intent.IntentResult;
import com.novax.leadora.application.usecase.chat.time.ChatDateRange;
import com.novax.leadora.infrastructure.integration.ai.ChatLlmService;
import com.novax.leadora.infrastructure.integration.ai.RagService;
import com.novax.leadora.infrastructure.persistence.entity.RoleEntity;
import com.novax.leadora.infrastructure.persistence.entity.UserEntity;
import com.novax.leadora.infrastructure.persistence.repository.AiDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A turn must end with an answer, whatever goes wrong on the way.
 *
 * <p>The question is persisted at the start of a turn, so any failure after that point used to
 * leave the conversation holding a question nothing ever replied to — and because a chat feed is
 * reloaded from the database, that gap outlived the error. From the user's side it read as "the
 * assistant sometimes just doesn't answer", with nothing visible to report.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatTurnResilienceTest {

    private static final UUID SESSION_ID = UUID.randomUUID();

    @Nested
    @DisplayName("Context gathering degrades, it does not fail the turn")
    class Gathering {

        @Mock private CrmSnapshotService crmSnapshotService;
        @Mock private PerformanceSnapshotService performanceSnapshotService;
        @Mock private RagService ragService;
        @Mock private AiDocumentRepository documentRepository;

        private ContextAssembler assembler;

        @BeforeEach
        void setUp() {
            // Runs the "parallel" sources on the calling thread: this test is about failure
            // handling, and a real pool would only add scheduling noise to it.
            assembler = new ContextAssembler(crmSnapshotService, performanceSnapshotService,
                    ragService, documentRepository, Runnable::run);
        }

        /**
         * The branches that consult one source call it directly rather than through the wrapper
         * that swallows failures, so a dropped connection there escaped the whole pipeline.
         */
        @Test
        @DisplayName("a database failure yields empty context instead of an exception")
        void databaseFailureIsSwallowed() {
            when(crmSnapshotService.personalSnapshot(any(), any(), any()))
                    .thenThrow(new RuntimeException("connection is closed"));

            String[] out = new String[1];
            assertThatCode(() -> out[0] = assembler.assemble(ChatIntent.PERSONAL_DATA,
                    actor(), CrmArea.defaults(), "lead của tôi", ChatDateRange.allTime()))
                    .doesNotThrowAnyException();
            assertThat(out[0]).isEmpty();
        }

        @Test
        @DisplayName("the same holds for the team-wide branch")
        void teamBranchIsSwallowedToo() {
            when(crmSnapshotService.canSeeAllData(any())).thenReturn(true);
            when(crmSnapshotService.mentionedStaffSnapshot(any(), any(), anyString(), any()))
                    .thenThrow(new RuntimeException("pool timeout"));

            assertThatCode(() -> assembler.assemble(ChatIntent.TEAM_SUMMARY,
                    actor(), CrmArea.defaults(), "doanh số cả team", ChatDateRange.allTime()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a healthy source is still returned")
        void healthySourceStillWorks() {
            when(crmSnapshotService.personalSnapshot(any(), any(), any())).thenReturn("Leads: 3");

            assertThat(assembler.assemble(ChatIntent.PERSONAL_DATA, actor(),
                    CrmArea.defaults(), "lead của tôi", ChatDateRange.allTime()))
                    .isEqualTo("Leads: 3");
        }
    }

    @Nested
    @DisplayName("A failed turn is still recorded as an answer")
    class Recovery {

        @Mock private ChatTurnWriter turnWriter;
        @Mock private IntentClassifier intentClassifier;
        @Mock private DateRangeResolver dateRangeResolver;
        @Mock private ContextAssembler contextAssembler;
        @Mock private ChatLlmService chatLlmService;

        private StreamChatMessageUseCase useCase;

        @BeforeEach
        void setUp() {
            useCase = new StreamChatMessageUseCase(turnWriter, intentClassifier, dateRangeResolver,
                    contextAssembler, chatLlmService, Runnable::run);
            when(dateRangeResolver.resolve(anyString(), any())).thenReturn(ChatDateRange.allTime());
            when(turnWriter.complete(any(), anyString(), any()))
                    .thenReturn(ChatMessageResponse.builder().messageId(UUID.randomUUID()).build());
        }

        private void givenQuestionRecorded() {
            when(turnWriter.begin(any(), any(), anyString())).thenReturn(new ChatTurnContext(
                    ChatMessageResponse.builder().messageId(UUID.randomUUID()).build(),
                    List.of(), null));
        }

        /**
         * The gap this closes: the question was written, the turn then blew up, and nothing was
         * ever written back. Reloading the conversation showed the question alone, for ever.
         */
        @Test
        @DisplayName("a failure after the question is recorded still writes a reply")
        void failureStillProducesAnAssistantMessage() {
            givenQuestionRecorded();
            when(intentClassifier.classify(anyString(), any(), anyBoolean()))
                    .thenThrow(new RuntimeException("boom"));

            useCase.execute(SESSION_ID, user(), "lead hôm nay");

            ArgumentCaptor<String> reply = ArgumentCaptor.forClass(String.class);
            verify(turnWriter).complete(any(), reply.capture(), isNull());
            assertThat(reply.getValue()).isNotBlank();
        }

        /**
         * BR-36: if the question was never recorded the session may not even belong to this
         * caller, so writing a reply into it would be worse than staying silent.
         */
        @Test
        @DisplayName("a failure BEFORE the question is recorded writes nothing")
        void failureBeforeRecordingWritesNothing() {
            when(turnWriter.begin(any(), any(), anyString()))
                    .thenThrow(new RuntimeException("session not found"));

            useCase.execute(SESSION_ID, user(), "lead hôm nay");

            verify(turnWriter, never()).complete(any(), anyString(), any());
        }

        @Test
        @DisplayName("a normal turn is unaffected")
        void healthyTurnStillAnswers() {
            givenQuestionRecorded();
            when(intentClassifier.classify(anyString(), any(), anyBoolean()))
                    .thenReturn(IntentResult.of(ChatIntent.GENERAL_BUSINESS));
            when(contextAssembler.assemble(any(), any(), any(), anyString(), any())).thenReturn("");
            when(chatLlmService.stream(anyString(), any(), anyString(), anyBoolean()))
                    .thenReturn(reactor.core.publisher.Flux.just("Chào ", "bạn"));

            SseEmitter emitter = useCase.execute(SESSION_ID, user(), "xin chào");

            assertThat(emitter).isNotNull();
            verify(turnWriter).complete(any(), anyString(), anyString());
        }
    }

    private static UserEntity user() {
        return UserEntity.builder()
                .userId(UUID.randomUUID())
                .fullName("Trần Nhật Minh")
                .role(RoleEntity.builder().roleId(1).roleName("SALES").build())
                .build();
    }

    private static ChatActor actor() {
        return ChatActor.from(user());
    }
}
