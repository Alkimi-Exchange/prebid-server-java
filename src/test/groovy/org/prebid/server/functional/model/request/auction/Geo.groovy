package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Geo {

    Float lat
    Float lon
    Integer type
    Integer accuracy
    Integer lastfix
    Integer ipservice
    String country
    String region
    String regionfips104
    String metro
    String city
    String zipl
    Integer utcoffset

    static Geo getGeo() {
        new Geo(lat: PBSUtils.getFractionalRandomNumber(0, 90),
                lon: PBSUtils.getFractionalRandomNumber(0, 90))
    }
}
