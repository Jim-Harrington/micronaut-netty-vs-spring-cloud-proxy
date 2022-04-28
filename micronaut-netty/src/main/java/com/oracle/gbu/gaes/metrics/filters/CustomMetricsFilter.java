package com.oracle.gbu.gaes.metrics.filters;


import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.netty.DefaultHttpClient;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Filter("/proxy/**")
@ExecuteOn(TaskExecutors.IO)
public class CustomMetricsFilter implements HttpServerFilter {

  private static final String GBUID_PREFIX = "gbuid.";
  private static final String CUSTOM_METRIC_TYPE = "http_server_requests";
  private static final String CUSTOM_METRIC_TAG_EXCEPTION = "exception";
  private static final String CUSTOM_METRIC_TAG_METHOD = "method";
  private static final String CUSTOM_METRIC_TAG_OUTCOME = "outcome";
  private static final String CUSTOM_METRIC_TAG_STATUS = "status";
  private static final String CUSTOM_METRIC_TAG_URI = "uri";
  private static final List<String> EXCLUDED_PATHS =
      List.of("/health", "/health/liveness", "/health/readiness", "/prometheus");


  private MeterRegistry metricRegistry = null;
  private final ProxyHttpClient client;

  public CustomMetricsFilter(MeterRegistry metricRegistry,
                             @Client("${gaes.forward-to}") ProxyHttpClient client) {
    this.metricRegistry = metricRegistry;
    this.client = client;
  }

  @Override
  public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

    if (EXCLUDED_PATHS.contains(request.getPath())) {
      return chain.proceed(request);
    }

    final String newPath = request.getPath().substring("/proxy".length());

    final long startTime = System.currentTimeMillis();

    return Publishers.map(client.proxy(request.mutate().uri(b -> b.replacePath(newPath))),
                          mutableHttpResponse -> {

                            // Post-processing
                            String path = excludePathParams(request.getUri());
                            String method = request.getMethodName();
                            String status = mutableHttpResponse.status().toString();
                            String outcome = ((status.startsWith("2")) ? "SUCCESS" : "FAIL");
                            long delta = System.currentTimeMillis() - startTime;
                            
                            ArrayList<io.micrometer.core.instrument.Tag> tags = new ArrayList<>();
                            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_EXCEPTION, "None"));
                            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_METHOD, method));
                            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_OUTCOME, outcome));
                            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_STATUS, status));
                            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_URI, path));
                            
                            metricRegistry.timer(CUSTOM_METRIC_TYPE, tags).record(delta, java.util.concurrent.TimeUnit.MILLISECONDS);
                            
                            return mutableHttpResponse;
                          });

  }

  private String excludePathParams(URI routeUri) {
    StringBuilder rtn = new StringBuilder();
    String uri = routeUri.toString();

    if (uri.indexOf(client.toString()) == 0) {
      uri = uri.substring(client.toString().length());
    }

    String[] uriTokens = uri.split("/");

    for (String token : uriTokens) {

      if ((token != null) && (token.length() > 0)) {

        String val = ((isParameter(token)) ? "{arg}" : token);
        rtn.append("/").append(val);
      }
    }

    return rtn.toString();
  }

  private boolean isParameter(String value) {
    int valueLen = value.length();
    boolean containsNumber = false;
    boolean containsLetter = false;
    boolean containsPunctuation = ((value.contains(".")) || (value.contains("-")));
    boolean isGbuid = value.startsWith(GBUID_PREFIX);

    if (!isGbuid && !containsPunctuation) {
      for (int i = 0; ((( !containsNumber ) || ( !containsLetter )) &&
                       ( i < valueLen ));
           ++i) {
        char val = value.charAt(i);
        containsNumber = (containsNumber || Character.isDigit(val));
        containsLetter = (containsLetter || Character.isLetter(val));
      }
    }

    return ((isGbuid) ||
            (containsPunctuation) ||
            ((containsNumber) && (containsLetter)));
  }
}
