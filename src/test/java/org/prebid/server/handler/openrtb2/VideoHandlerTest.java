package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.video.PodError;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.HooksMetricsService;
import org.prebid.server.auction.VideoResponseFactory;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.CachedDebugLog;
import org.prebid.server.auction.model.TimeoutContext;
import org.prebid.server.auction.model.WithPodErrors;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.auction.requestfactory.VideoRequestFactory;
import org.prebid.server.cache.CoreCacheService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookExecutionContext;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.hooks.execution.v1.exitpoint.ExitpointPayloadImpl;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.response.VideoResponse;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class VideoHandlerTest extends VertxTest {

    @Mock
    private VideoRequestFactory videoRequestFactory;
    @Mock
    private VideoResponseFactory videoResponseFactory;
    @Mock
    private ExchangeService exchangeService;
    @Mock
    private CoreCacheService coreCacheService;
    @Mock
    private AnalyticsReporterDelegator analyticsReporterDelegator;
    @Mock
    private Metrics metrics;
    @Mock
    private Clock clock;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock(strictness = LENIENT)
    private HttpServerResponse httpResponse;
    @Mock
    private UidsCookie uidsCookie;
    @Mock
    private PrebidVersionProvider prebidVersionProvider;
    @Mock(strictness = LENIENT)
    private HooksMetricsService hooksMetricsService;
    @Mock(strictness = LENIENT)
    private HookStageExecutor hookStageExecutor;

    private VideoHandler target;

    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.params()).willReturn(MultiMap.caseInsensitiveMultiMap());
        given(httpRequest.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(httpResponse.exceptionHandler(any())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);
        given(httpResponse.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        given(clock.millis()).willReturn(Instant.now().toEpochMilli());

        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willAnswer(invocation -> Future.succeededFuture(HookStageExecutionResult.of(
                        false,
                        ExitpointPayloadImpl.of(invocation.getArgument(0), invocation.getArgument(1)))));

        given(hooksMetricsService.updateHooksMetrics(any())).willAnswer(invocation -> invocation.getArgument(0));

        timeout = new TimeoutFactory(clock).create(2000L);

        target = new VideoHandler(
                videoRequestFactory,
                videoResponseFactory,
                exchangeService, coreCacheService,
                analyticsReporterDelegator,
                metrics,
                hooksMetricsService,
                clock,
                prebidVersionProvider,
                hookStageExecutor,
                jacksonMapper);
    }

    @Test
    public void shouldUseTimeoutFromAuctionContext() {
        // given
        final WithPodErrors<AuctionContext> auctionContext = givenAuctionContext(identity(), emptyList());
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContext));

        givenHoldAuction(BidResponse.builder().build());

        // when
        target.handle(routingContext);

        // then
        assertThat(captureAuctionContext())
                .extracting(AuctionContext::getTimeoutContext)
                .extracting(TimeoutContext::getTimeout)
                .extracting(Timeout::remaining)
                .isEqualTo(2000L);
    }

    @Test
    public void shouldAddPrebidVersionResponseHeader() {
        // given
        given(prebidVersionProvider.getNameVersionRecord()).willReturn("pbs-java/1.00");

        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(BidResponse.builder().build())
                        .build()));

        // when
        target.handle(routingContext);

        // then
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("x-prebid", "pbs-java/1.00"));
    }

    @Test
    public void shouldAddObserveBrowsingTopicsResponseHeader() {
        // given
        httpRequest.headers().add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, "");

        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(BidResponse.builder().build())
                        .build()));

        // when
        target.handle(routingContext);

        // then
        assertThat(httpResponse.headers())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .contains(tuple("Observe-Browsing-Topics", "?1"));
    }

    @Test
    public void shouldComputeTimeoutBasedOnRequestProcessingStartTime() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        givenHoldAuction(BidResponse.builder().build());

        final Instant now = Instant.now();
        given(clock.millis()).willReturn(now.toEpochMilli()).willReturn(now.plusMillis(50L).toEpochMilli());

        // when
        target.handle(routingContext);

        // then
        assertThat(captureAuctionContext())
                .extracting(AuctionContext::getTimeoutContext)
                .extracting(TimeoutContext::getTimeout)
                .extracting(Timeout::remaining)
                .asInstanceOf(InstanceOfAssertFactories.LONG)
                .isLessThanOrEqualTo(1950L);
    }

    @Test
    public void shouldRespondWithBadRequestIfBidRequestIsInvalid() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new InvalidRequestException("Request is invalid")));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end(eq("Invalid request format: Request is invalid"));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithUnauthorizedIfAccountIdIsInvalid() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new UnauthorizedAccountException("Account id is not provided", "1")));

        // when
        target.handle(routingContext);

        // then
        verifyNoInteractions(exchangeService);
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end(eq("Unauthorised: Account id is not provided"));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithInternalServerErrorIfAuctionFails() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        given(exchangeService.holdAuction(any()))
                .willThrow(new RuntimeException("Unexpected exception"));

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(500));
        verify(httpResponse).end(eq("Critical error while running the auction: Unexpected exception"));
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldNotSendResponseIfClientClosedConnection() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.failedFuture(new RuntimeException()));

        given(routingContext.response().closed()).willReturn(true);

        // when
        target.handle(routingContext);

        // then
        verify(httpResponse, never()).end(anyString());
        verifyNoInteractions(hooksMetricsService, hookStageExecutor);
    }

    @Test
    public void shouldRespondWithBidResponse() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        givenHoldAuction(BidResponse.builder().build());

        given(videoResponseFactory.toVideoResponse(any(), any(), any()))
                .willReturn(VideoResponse.of(emptyList(), null));

        // when
        target.handle(routingContext);

        // then
        verify(videoResponseFactory, times(2)).toVideoResponse(any(), any(), any());

        assertThat(httpResponse.headers()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));
        verify(httpResponse).end(eq("{\"adPods\":[]}"));

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{\"adPods\":[]}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldRespondWithBidResponseWhenExitpointHookChangesResponseAndHeaders() {
        // given
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(givenAuctionContext(identity(), emptyList())));

        givenHoldAuction(BidResponse.builder().build());

        given(videoResponseFactory.toVideoResponse(any(), any(), any()))
                .willReturn(VideoResponse.of(emptyList(), null));

        given(hookStageExecutor.executeExitpointStage(any(), any(), any()))
                .willReturn(Future.succeededFuture(HookStageExecutionResult.success(
                        ExitpointPayloadImpl.of(
                                MultiMap.caseInsensitiveMultiMap().add("New-Header", "New-Header-Value"),
                                "{\"adPods\":[{\"something\":1}]}"))));

        // when
        target.handle(routingContext);

        // then
        verify(videoResponseFactory, times(2)).toVideoResponse(any(), any(), any());

        assertThat(httpResponse.headers()).hasSize(1)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsExactlyInAnyOrder(tuple("New-Header", "New-Header-Value"));
        verify(httpResponse).end(eq("{\"adPods\":[{\"something\":1}]}"));

        final ArgumentCaptor<MultiMap> responseHeadersCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(hookStageExecutor).executeExitpointStage(
                responseHeadersCaptor.capture(),
                eq("{\"adPods\":[]}"),
                any());

        assertThat(responseHeadersCaptor.getValue()).hasSize(2)
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple("Content-Type", "application/json"),
                        tuple("x-prebid", "pbs-java/1.00"));

        verify(hooksMetricsService).updateHooksMetrics(any());
    }

    @Test
    public void shouldUpdateVideoEventWithCacheLogIdErrorAndCallCacheForDebugLogWhenStatusIsNot200oK() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().imp(emptyList()).build())
                .account(Account.builder().auction(AccountAuctionConfig.builder().videoCacheTtl(100).build()).build())
                .cachedDebugLog(new CachedDebugLog(true, 10, null, jacksonMapper))
                .build();

        final WithPodErrors<AuctionContext> auctionContextWithPodErrors = WithPodErrors.of(auctionContext, emptyList());
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContextWithPodErrors));
        given(exchangeService.holdAuction(any())).willThrow(new RuntimeException("Unexpected exception"));
        given(coreCacheService.cacheVideoDebugLog(any(), anyInt())).willReturn("cacheKey");

        // when
        target.handle(routingContext);

        // then
        verify(coreCacheService).cacheVideoDebugLog(any(), anyInt());
        final ArgumentCaptor<VideoEvent> videoEventArgumentCaptor = ArgumentCaptor.forClass(VideoEvent.class);
        verify(analyticsReporterDelegator).processEvent(videoEventArgumentCaptor.capture(), any());
        assertThat(videoEventArgumentCaptor.getValue().getErrors())
                .contains("[Debug cache ID: cacheKey]");
    }

    @Test
    public void shouldCacheDebugLogWhenNoBidsWereReturnedAndDoesNotAddErrorToVideoEvent() {
        // given
        final CachedDebugLog cachedDebugLog = new CachedDebugLog(true, 10, null, jacksonMapper);
        cachedDebugLog.setHasBids(false);
        final AuctionContext auctionContext = AuctionContext.builder()
                .bidRequest(BidRequest.builder().imp(emptyList()).build())
                .account(Account.builder().auction(AccountAuctionConfig.builder().videoCacheTtl(100).build()).build())
                .debugContext(DebugContext.empty())
                .cachedDebugLog(cachedDebugLog)
                .build();

        final WithPodErrors<AuctionContext> auctionContextWithPodErrors = WithPodErrors.of(auctionContext, emptyList());
        given(videoRequestFactory.fromRequest(any(), anyLong()))
                .willReturn(Future.succeededFuture(auctionContextWithPodErrors));
        given(coreCacheService.cacheVideoDebugLog(any(), anyInt())).willReturn("cacheKey");
        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(BidResponse.builder().build())
                        .build()));

        given(videoResponseFactory.toVideoResponse(any(), any(), any()))
                .willReturn(VideoResponse.of(emptyList(), null));

        // when
        target.handle(routingContext);

        // then
        verify(coreCacheService).cacheVideoDebugLog(any(), anyInt());
        final ArgumentCaptor<VideoEvent> videoEventArgumentCaptor = ArgumentCaptor.forClass(VideoEvent.class);
        verify(analyticsReporterDelegator).processEvent(videoEventArgumentCaptor.capture(), any());
        assertThat(videoEventArgumentCaptor.getValue().getErrors())
                .doesNotContain("[Debug cache ID: cacheKey]");
    }

    private AuctionContext captureAuctionContext() {
        final ArgumentCaptor<AuctionContext> captor = ArgumentCaptor.forClass(AuctionContext.class);
        verify(exchangeService).holdAuction(captor.capture());
        return captor.getValue();
    }

    private void givenHoldAuction(BidResponse bidResponse) {
        given(exchangeService.holdAuction(any()))
                .willAnswer(inv -> Future.succeededFuture(((AuctionContext) inv.getArgument(0)).toBuilder()
                        .bidResponse(bidResponse)
                        .build()));
    }

    private WithPodErrors<AuctionContext> givenAuctionContext(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestBuilderCustomizer,
            List<PodError> errors) {
        final BidRequest bidRequest = bidRequestBuilderCustomizer.apply(BidRequest.builder()
                .imp(emptyList())).build();

        final AuctionContext auctionContext = AuctionContext.builder()
                .cachedDebugLog(new CachedDebugLog(false, 100, null, jacksonMapper))
                .uidsCookie(uidsCookie)
                .bidRequest(bidRequest)
                .timeoutContext(TimeoutContext.of(0, timeout, 0))
                .debugContext(DebugContext.of(true, false, TraceLevel.verbose))
                .hookExecutionContext(HookExecutionContext.of(Endpoint.openrtb2_video))
                .build();

        return WithPodErrors.of(auctionContext, errors);
    }
}
