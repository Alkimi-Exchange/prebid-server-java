package org.prebid.server.spring.config.bidder;

import org.prebid.server.bidder.BidderDeps;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.pulsepoint.PulsepointAdapter;
import org.prebid.server.bidder.pulsepoint.PulsepointBidder;
import org.prebid.server.proto.response.BidderInfo;
import org.prebid.server.spring.config.bidder.model.BidderConfigurationProperties;
import org.prebid.server.spring.config.bidder.model.MetaInfo;
import org.prebid.server.spring.config.bidder.model.UsersyncConfigurationProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import javax.validation.constraints.NotBlank;

@Configuration
@PropertySource(value = "classpath:/bidder-config/pulsepoint.yaml", factory = YamlPropertySourceFactory.class)
public class PulsepointConfiguration {

    private static final String BIDDER_NAME = "pulsepoint";

    @Autowired
    @Qualifier("pulsepointConfigurationProperties")
    private BidderConfigurationProperties configProperties;

    @Value("${external-url}")
    @NotBlank
    private String externalUrl;

    @Bean("pulsepointConfigurationProperties")
    @ConfigurationProperties("adapters.pulsepoint")
    BidderConfigurationProperties configurationProperties() {
        return new BidderConfigurationProperties();
    }

    @Bean
    BidderDeps pulsepointBidderDeps() {
        final MetaInfo metaInfo = configProperties.getMetaInfo();
        final BidderInfo bidderInfo = BidderInfo.create(configProperties.getEnabled(), metaInfo.getMaintainerEmail(),
                metaInfo.getAppMediaTypes(), metaInfo.getSiteMediaTypes(), metaInfo.getSupportedVendors(),
                metaInfo.getVendorId(), configProperties.getPbsEnforcesGdpr());

        final UsersyncConfigurationProperties usersyncProperties = configProperties.getUsersync();
        final Usersyncer usersyncer = new Usersyncer(usersyncProperties.getCookieFamilyName(),
                usersyncProperties.getUrl(), usersyncProperties.getRedirectUrl(), externalUrl,
                usersyncProperties.getType(), usersyncProperties.getSupportCors());

        return BidderDepsAssembler.forBidder(BIDDER_NAME)
                .withConfig(configProperties)
                .bidderInfo(bidderInfo)
                .usersyncer(usersyncer)
                .bidderCreator(() -> new PulsepointBidder(configProperties.getEndpoint()))
                .adapterCreator(() -> new PulsepointAdapter(usersyncer, configProperties.getEndpoint()))
                .assemble();
    }
}
