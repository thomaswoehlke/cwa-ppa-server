package app.coronawarn.datadonation.services.retention;

import app.coronawarn.datadonation.services.retention.config.RetentionConfiguration;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@SpringBootApplication
@EnableJdbcRepositories(basePackages = "app.coronawarn.datadonation.common.persistence")
@EntityScan(basePackages = "app.coronawarn.datadonation.common.persistence.domain")
@ComponentScan({"app.coronawarn.datadonation.common.persistence", "app.coronawarn.datadonation.services.retention"})
@EnableConfigurationProperties(RetentionConfiguration.class)
public class Application implements DisposableBean, EnvironmentAware {

  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) {
    SpringApplication.run(Application.class);
  }

  /**
   * Terminates this application with exit code 1 (general error).
   */
  public static void killApplication(ApplicationContext appContext) {
    SpringApplication.exit(appContext);
    logger.error("Application terminated abnormally.");
    System.exit(1);
  }

  /**
   * Manual shutdown hook needed to avoid Log4j shutdown issues (see cwa-server/#589).
   */
  @Override
  public void destroy() {
    logger.info("Shutting down log4j2.");
    LogManager.shutdown();
  }

  @Override
  public void setEnvironment(Environment environment) {
    List<String> profiles = Arrays.asList(environment.getActiveProfiles());
    logger.info("Enabled named groups: {}", System.getProperty("jdk.tls.namedGroups"));
    if (profiles.contains("disable-ssl-client-postgres")) {
      logger.warn("The retention service is started with postgres connection TLS disabled. "
          + "This should never be used in PRODUCTION!");
    }
  }
}
