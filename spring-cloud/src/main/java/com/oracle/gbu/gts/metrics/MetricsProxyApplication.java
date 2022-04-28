package com.oracle.gbu.gts.metrics;

import java.net.URI;
import java.util.*;
import javax.annotation.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.context.annotation.ComponentScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@SpringBootApplication
@EnableAutoConfiguration
@RestController
@RequestMapping(path="/proxy")
@ComponentScan("com.oracle.gbu.gts.metrics")
public class MetricsProxyApplication
{
  private static final Logger log = LoggerFactory.getLogger(MetricsProxyApplication.class);
  private static final String CUSTOM_METRIC_TYPE = "http_server_requests";
  private static final String CUSTOM_METRIC_TAG_EXCEPTION = "exception";
  private static final String CUSTOM_METRIC_TAG_METHOD = "method";
  private static final String CUSTOM_METRIC_TAG_OUTCOME = "outcome";
  private static final String CUSTOM_METRIC_TAG_STATUS = "status";
  private static final String CUSTOM_METRIC_TAG_URI = "uri";
  private static final String GBUID_PREFIX = "gbuid.";
  
  private final Environment env;
  private final ApplicationContext ctx;
  private String forwardTo = null;
  private WebServer http = null;
  private MeterRegistry metricRegistry = null;

  @Value("${server.http.port}")
  private int httpPort;

  @Autowired
  public MetricsProxyApplication(MeterRegistry metricRegistry,
                                 Environment env,
                                 ApplicationContext ctx)
  {
    this.env = env;
    this.forwardTo = this.env.getProperty(Constants.GTS_FORWARD_TO_KEY);
    this.ctx = ctx;
    this.metricRegistry = metricRegistry;
  }

  @Bean
  public RouteLocator customRouteLocator(RouteLocatorBuilder builder)
  {
    return builder.routes()
      .route("path_route", r -> r.path("/proxy/**")
             .filters(f -> f.rewritePath("^/proxy", ""))
             .uri(forwardTo))
      .build();
  }

  //
  // Process lifecycle
  //

  @PostConstruct
  public void start()
    throws Exception
  {
    HttpHandler httpHandler = getHttpHandler();
    ReactiveWebServerFactory factory = new NettyReactiveWebServerFactory(httpPort);
    this.http = factory.getWebServer(httpHandler);
    this.http.start();
  }
  
  @PreDestroy
  public void stop()
  {
    this.http.stop();
  }
  
  //
  // Custom Metrics
  //

  @Component
  public class CustomMetricsFilter
    implements GlobalFilter
  {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
    {
      final long startTime = System.currentTimeMillis();
      
      return chain.filter(exchange)
      .then(Mono.fromRunnable(() -> {
            // Post-processing
            URI routeUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
            String path = excludePathParams(routeUri);
            String otherPath = exchange.getRequest().getPath().contextPath().value();
            String method = exchange.getRequest().getMethodValue();
            String status = exchange.getResponse().getRawStatusCode().toString();
            String outcome = ((status.startsWith("2")) ? "SUCCESS" : "FAIL");
            long delta = System.currentTimeMillis() - startTime;

            ArrayList<io.micrometer.core.instrument.Tag> tags = new ArrayList<>();
            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_EXCEPTION, "None"));
            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_METHOD, method));
            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_OUTCOME, outcome));
            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_STATUS, status));
            tags.add(new io.micrometer.core.instrument.ImmutableTag(CUSTOM_METRIC_TAG_URI, path));
            
            metricRegistry.timer(CUSTOM_METRIC_TYPE, tags).record(delta, java.util.concurrent.TimeUnit.MILLISECONDS);
          }
          )
        );
    }

    private String excludePathParams(URI routeUri)
    {
      StringBuilder rtn = new StringBuilder();
      String uri = routeUri.toString();

      if (uri.indexOf(forwardTo) == 0)
      {
        uri = uri.substring(forwardTo.length());
      }

      String[] uriTokens = uri.split("/");
      for (String token : uriTokens)
      {
        if ((token != null) &&
            (token.length() > 0))
        {
          String val = ((isParameter(token)) ? "{arg}" : token);
          rtn.append("/").append(val);
        }
      }

      return rtn.toString();
    }

    private boolean isParameter(String value)
    {
      int valueLen = value.length();
      boolean containsNumber = false;
      boolean containsLetter = false;
      boolean containsPunctuation = ((value.indexOf(".") >= 0) ||
                                     (value.indexOf("-") >= 0));
      boolean isGbuid = value.startsWith(GBUID_PREFIX);

      if ( !isGbuid && !containsPunctuation )
      {
        for (int i = 0; ((( !containsNumber ) || ( !containsLetter )) &&
                         ( i < valueLen ));
             ++i)
        {
          char val = value.charAt(i);
          containsNumber = (containsNumber || Character.isDigit​(val));
          containsLetter = (containsLetter || Character.isLetter(val));
        }
      }

      return ((isGbuid) ||
              (containsPunctuation) ||
              ((containsNumber) && (containsLetter)));
    }
  }

  //
  // Helpers
  //

  private HttpHandler getHttpHandler()
        throws Exception
  {
    HttpHandler rtn = null;

    Map<String, HttpHandler> beans = ctx.getBeansOfType( HttpHandler.class );
    rtn = beans.values().iterator().next();
    
    return rtn;
  }

  //
  // Main
  //

  public static void main(String[] args)
  {
    SpringApplication.run(MetricsProxyApplication.class, args);
  }

  public static ConfigurableApplicationContext start(String[] args)
  {
    return SpringApplication.run(MetricsProxyApplication.class, args);
  }

  public static void stop(ConfigurableApplicationContext context)
  {
    SpringApplication.exit(context, () -> 0);
  }
}
