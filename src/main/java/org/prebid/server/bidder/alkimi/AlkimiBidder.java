package org.prebid.server.bidder.alkimi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.alkimi.ExtImpAlkimi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AlkimiBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private static final TypeReference<ExtPrebid<?, ExtImpAlkimi>> ALKIMI_EXT_TYPE_REFERENCE = new TypeReference<>() {
    };

    public AlkimiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        List<Imp> imps = request.getImp();
        final List<Imp> updatedImps = new ArrayList<>(imps.size());

        imps.forEach(imp -> {
            ExtImpAlkimi ext = parseImpExt(imp);
            updatedImps.add(updateImp(imp, ext));
        });

        final BidRequest outgoingRequest = request.toBuilder().imp(updatedImps).build();
        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(endpointUrl)
                                .headers(HttpUtil.headers())
                                .payload(outgoingRequest)
                                .body(mapper.encodeToBytes(outgoingRequest))
                                .build()
                ),
                Collections.emptyList()
        );
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        BidType bidType = BidType.banner;
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return bidType;
                } else if (imp.getVideo() != null) {
                    bidType = BidType.video;
                } else if (imp.getXNative() != null) {
                    bidType = BidType.xNative;
                } else if (imp.getAudio() != null) {
                    bidType = BidType.audio;
                }
            }
        }
        return bidType;
    }

    private ExtImpAlkimi parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), ALKIMI_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing bidder ext in impression with id: " + imp.getId());
        }
    }

    private static Imp updateImp(Imp imp, ExtImpAlkimi ext) {
        BigDecimal bidFloor = ext.getBidFloor();
        Integer position = ext.getPos();

        Imp.ImpBuilder impBuilder = imp.toBuilder();
        impBuilder.bidfloor(bidFloor);

        if (imp.getBanner() != null) {
            final Banner banner = imp.getBanner();
            if (CollectionUtils.isNotEmpty(banner.getFormat())) {
                final Format firstFormat = banner.getFormat().get(0);
                impBuilder
                        .banner(banner.toBuilder()
                                .w(firstFormat.getW())
                                .h(firstFormat.getH())
                                .pos(position)
                                .build());
            }
        }

        if (imp.getVideo() != null) {
            Video video = imp.getVideo();
            impBuilder
                    .video(video.toBuilder()
                            .pos(position)
                            .build());
        }

        return impBuilder.build();
    }
}
