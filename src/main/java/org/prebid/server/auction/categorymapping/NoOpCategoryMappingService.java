package org.prebid.server.auction.categorymapping;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.model.CategoryMappingResult;
import org.prebid.server.execution.timeout.Timeout;

import java.util.List;

public class NoOpCategoryMappingService implements CategoryMappingService {

    @Override
    public Future<CategoryMappingResult> createCategoryMapping(List<BidderResponse> bidderResponses,
                                                               BidRequest bidRequest,
                                                               Timeout timeout) {

        return Future.succeededFuture(CategoryMappingResult.of(bidderResponses));
    }
}
