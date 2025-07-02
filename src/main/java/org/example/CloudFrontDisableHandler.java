package org.example;

import com.amazonaws.services.cloudfront.AmazonCloudFront;
import com.amazonaws.services.cloudfront.AmazonCloudFrontClientBuilder;
import com.amazonaws.services.cloudfront.model.DistributionConfig;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigRequest;
import com.amazonaws.services.cloudfront.model.GetDistributionConfigResult;
import com.amazonaws.services.cloudfront.model.UpdateDistributionRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import java.util.*;
import java.util.stream.Collectors;

public class CloudFrontDisableHandler implements RequestHandler<SNSEvent, Map<String, Object>> {
    private final AmazonCloudFront cloudFront = AmazonCloudFrontClientBuilder.defaultClient();

    @Override
    public Map<String, Object> handleRequest(SNSEvent snsEvent, Context context) {
        List<String> distributionFromEnv = getDistributionFromEnv();
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> results = new ArrayList<>();

        if (distributionFromEnv.isEmpty()) {
            response.put("status","error");
            response.put("message","No DISTRIBUTION_IDS found in environment variables");
            return response;
        }

        for (String id : distributionFromEnv) {
            Map<String, String> result = disableDistribution(id);
            results.add(result);
        }
        response.put("status", "completed");
        response.put("results", results);
        return response;
    }

    private List<String> getDistributionFromEnv() {
        String env = System.getenv("DISTRIBUTION_IDS");

        if (env == null || env.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(env.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toList());
    }

    private Map<String, String> disableDistribution(String distributionId) {
        Map<String, String> response = new HashMap<>();

        try {

            GetDistributionConfigResult getResult = cloudFront.getDistributionConfig(
                    new GetDistributionConfigRequest().withId(distributionId)
            );
            String etag = getResult.getETag();
            DistributionConfig config = getResult.getDistributionConfig();

            config.setEnabled(false);

            UpdateDistributionRequest updateRequest = new UpdateDistributionRequest()
                    .withId(distributionId)
                    .withIfMatch(etag)
                    .withDistributionConfig(config);

            cloudFront.updateDistribution(updateRequest);
            response.put( "distribution_id", distributionId);
            response.put( "status", "disabled");

            return response;

        } catch (Exception e) {
            response.put( "distribution_id", distributionId);
            response.put( "status", "error");
            response.put( "message", e.getMessage());
            return response;
        }
    }
}
