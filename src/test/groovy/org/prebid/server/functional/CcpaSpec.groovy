package org.prebid.server.functional

import org.prebid.server.functional.model.config.AccountCcpaConfig
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.model.config.AccountPrivacyConfig
import org.prebid.server.functional.model.db.Account
import org.prebid.server.functional.model.db.StoredRequest
import org.prebid.server.functional.model.request.amp.AmpRequest
import org.prebid.server.functional.model.request.auction.App
import org.prebid.server.functional.model.request.auction.BidRequest
import org.prebid.server.functional.model.request.auction.Device
import org.prebid.server.functional.model.request.auction.Geo
import org.prebid.server.functional.model.request.auction.RegsExt
import org.prebid.server.functional.model.request.auction.Site
import org.prebid.server.functional.testcontainers.PBSTest
import org.prebid.server.functional.util.PBSUtils
import spock.lang.Unroll

import static org.prebid.server.functional.model.ChannelType.AMP
import static org.prebid.server.functional.model.ChannelType.APP
import static org.prebid.server.functional.model.ChannelType.WEB

@PBSTest
class CcpaSpec extends BaseSpec {

    // TODO: extend ccpa test with actual fields that we should mask
    def "PBS should mask publisher info when privacy.ccpa.enabled = true in account config"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def valid_ccpa = "1YYY"
        bidRequest.regs.ext = new RegsExt(gdpr: 0, usPrivacy: valid_ccpa)
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: true)
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == PBSUtils.getRoundFractionalNumber(lat, 2)
        assert bidderRequests.device?.geo?.lon == PBSUtils.getRoundFractionalNumber(lon, 2)
    }

    // TODO: extend this ccpa test with actual fields that we should mask
    def "PBS should not mask publisher info when privacy.ccpa.enabled = false in account config"() {
        given: "Default basic generic BidRequest"
        def bidRequest = BidRequest.defaultBidRequest
        def valid_ccpa = "1YYY"
        bidRequest.regs.ext = new RegsExt(gdpr: 0, usPrivacy: valid_ccpa)
        def lat = PBSUtils.getFractionalRandomNumber(0, 90)
        def lon = PBSUtils.getFractionalRandomNumber(0, 90)
        bidRequest.device = new Device(geo: new Geo(lat: lat, lon: lon))

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: false)
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: bidRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain not masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == lat
        assert bidderRequests.device?.geo?.lon == lon
    }

    @Unroll
    def "PBS should apply ccpa when privacy.ccpa.channel-enabled.#channel = true in account config"() {
        given: "Default basic generic BidRequest"
        def valid_ccpa = "1YY-"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext = new RegsExt(usPrivacy: valid_ccpa)
            device = new Device(geo: Geo.geo)
            site = requestSite
            app = requestApp
        }

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: ccpaEnabled, enabledForRequestType: [(channel): true])
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == PBSUtils.getRoundFractionalNumber(bidRequest.device.geo.lat, 2)
        assert bidderRequests.device?.geo?.lon == PBSUtils.getRoundFractionalNumber(bidRequest.device.geo.lon, 2)

        where:
        channel | ccpaEnabled | requestSite      | requestApp     | accountId
        WEB     | false       | Site.defaultSite | null           | requestSite.publisher.id
        WEB     | true        | Site.defaultSite | null           | requestSite.publisher.id
        APP     | true        | null             | App.defaultApp | requestApp.id
        APP     | false       | null             | App.defaultApp | requestApp.id
    }

    @Unroll
    def "PBS should not apply ccpa when privacy.ccpa.channel-enabled.#channel = false in account config"() {
        given: "Default basic generic BidRequest"
        def valid_ccpa = "1YY-"
        def bidRequest = BidRequest.defaultBidRequest.tap {
            regs.ext = new RegsExt(usPrivacy: valid_ccpa)
            device = new Device(geo: Geo.geo)
            site = requestSite
            app = requestApp
        }

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: ccpaEnabled, enabledForRequestType: [(channel): false])
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: accountId, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes auction request"
        defaultPbsService.sendAuctionRequest(bidRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(bidRequest.id)
        assert bidderRequests.device?.geo?.lat == bidRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == bidRequest.device.geo.lon

        where:
        channel | ccpaEnabled | requestSite      | requestApp     | accountId
        WEB     | false       | Site.defaultSite | null           | requestSite.publisher.id
        WEB     | true        | Site.defaultSite | null           | requestSite.publisher.id
        APP     | true        | null             | App.defaultApp | requestApp.id
        APP     | false       | null             | App.defaultApp | requestApp.id
    }

    @Unroll
    def "PBS should apply ccpa when privacy.ccpa.channel-enabled.amp = true in account config"() {
        given: "Default AmpRequest"
        def validCcpa = "1YYY"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            gdprConsent = validCcpa
            consentType = 3
        }

        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
            device = new Device(geo: Geo.geo)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: ccpaEnabled, enabledForRequestType: [(AMP): true])
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: ampStoredRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.device?.geo?.lat == PBSUtils.getRoundFractionalNumber(ampStoredRequest.device.geo.lat, 2)
        assert bidderRequests.device?.geo?.lon == PBSUtils.getRoundFractionalNumber(ampStoredRequest.device.geo.lon, 2)

        where:
        ccpaEnabled << [true, false]
    }

    @Unroll
    def "PBS should not apply ccpa when privacy.ccpa.channel-enabled.amp = false in account config"() {
        given: "Default AmpRequest"
        def validCcpa = "1YYY"
        def ampRequest = AmpRequest.defaultAmpRequest.tap {
            gdprConsent = validCcpa
            consentType = 3
        }

        def ampStoredRequest = BidRequest.defaultBidRequest.tap {
            site.publisher.id = ampRequest.account
            device = new Device(geo: Geo.geo)
        }

        and: "Save storedRequest into DB"
        def storedRequest = StoredRequest.getDbStoredRequest(ampRequest, ampStoredRequest)
        storedRequestDao.save(storedRequest)

        and: "Save account config into DB"
        def ccpa = new AccountCcpaConfig(enabled: ccpaEnabled, enabledForRequestType: [(AMP): false])
        def privacy = new AccountPrivacyConfig(ccpa: ccpa)
        def accountConfig = new AccountConfig(privacy: privacy)
        def account = new Account(uuid: ampStoredRequest.site.publisher.id, config: accountConfig)
        accountDao.save(account)

        when: "PBS processes amp request"
        defaultPbsService.sendAmpRequest(ampRequest)

        then: "Bidder request should contain masked values"
        def bidderRequests = bidder.getBidderRequest(ampStoredRequest.id)
        assert bidderRequests.device?.geo?.lat == ampStoredRequest.device.geo.lat
        assert bidderRequests.device?.geo?.lon == ampStoredRequest.device.geo.lon

        where:
        ccpaEnabled << [true, false]
    }
}
