package com.oracle.gbu.gaes.metrics;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Import;
import io.micronaut.runtime.Micronaut;

@Import(classes = {
        com.oracle.gbu.gaes.metrics.controller.DemoController.class,
        com.oracle.gbu.gaes.metrics.filters.CustomMetricsFilter.class
})
public class MetricsProxyApplication {

    public static void main(String[] args) {
        Micronaut.run(MetricsProxyApplication.class, args);
    }

    public static ApplicationContext start(String[] args) {
        return Micronaut.run(MetricsProxyApplication.class, args);
    }

    public static void stop(ApplicationContext context) {
        context.stop();
    }
}
