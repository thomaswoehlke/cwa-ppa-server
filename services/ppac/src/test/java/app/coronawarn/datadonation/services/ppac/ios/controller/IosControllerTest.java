package app.coronawarn.datadonation.services.ppac.ios.controller;

import static app.coronawarn.datadonation.common.utils.TimeUtils.getZonedDateTimeFor;
import static app.coronawarn.datadonation.services.ppac.ios.testdata.TestData.buildBase64String;
import static app.coronawarn.datadonation.services.ppac.ios.testdata.TestData.buildIosDeviceData;
import static app.coronawarn.datadonation.services.ppac.ios.testdata.TestData.buildUuid;
import static app.coronawarn.datadonation.services.ppac.ios.testdata.TestData.jsonify;
import static app.coronawarn.datadonation.services.ppac.ios.testdata.TestData.postOtpCreationRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import app.coronawarn.datadonation.common.config.UrlConstants;
import app.coronawarn.datadonation.common.persistence.domain.OneTimePassword;
import app.coronawarn.datadonation.common.persistence.repository.ApiTokenRepository;
import app.coronawarn.datadonation.common.persistence.repository.DeviceTokenRepository;
import app.coronawarn.datadonation.common.persistence.service.OtpCreationResponse;
import app.coronawarn.datadonation.common.persistence.service.OtpService;
import app.coronawarn.datadonation.common.protocols.internal.ppdd.EDUSOneTimePassword;
import app.coronawarn.datadonation.common.protocols.internal.ppdd.EDUSOneTimePasswordRequestIOS;
import app.coronawarn.datadonation.common.protocols.internal.ppdd.PPACIOS;
import app.coronawarn.datadonation.services.ppac.config.PpacConfiguration;
import app.coronawarn.datadonation.services.ppac.config.TestBeanConfig;
import app.coronawarn.datadonation.services.ppac.ios.client.IosDeviceApiClient;
import app.coronawarn.datadonation.services.ppac.ios.client.domain.PerDeviceDataResponse;
import app.coronawarn.datadonation.services.ppac.ios.verification.JwtProvider;
import app.coronawarn.datadonation.services.ppac.ios.verification.apitoken.authentication.ApiTokenAuthenticationStrategy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestBeanConfig.class)
@ActiveProfiles("test")
@DirtiesContext
public class IosControllerTest {

  private static final String IOS_OTP_URL = UrlConstants.IOS + UrlConstants.OTP;

  @Autowired
  private TestRestTemplate testRestTemplate;

  @MockBean
  private IosDeviceApiClient iosDeviceApiClient;

  @MockBean
  JwtProvider jwtProvider;

  @MockBean
  ApiTokenAuthenticationStrategy apiTokenAuthenticationStrategy;

  @Autowired
  private PpacConfiguration ppacConfiguration;

  @SpyBean
  private OtpService otpService;

  @Autowired
  private ApiTokenRepository apiTokenRepository;

  @Autowired
  private DeviceTokenRepository deviceTokenRepository;

  @BeforeEach
  void clearDatabase() {
    apiTokenRepository.deleteAll();
    deviceTokenRepository.deleteAll();
  }

  private EDUSOneTimePasswordRequestIOS buildValidOtpPayload(String password) {
    PPACIOS ppacios = PPACIOS.newBuilder()
        .setApiToken(buildUuid())
        .setDeviceToken(buildBase64String(ppacConfiguration.getIos().getMinDeviceTokenLength() + 1))
        .build();

    return EDUSOneTimePasswordRequestIOS.newBuilder()
        .setAuthentication(ppacios)
        .setPayload(EDUSOneTimePassword.newBuilder().setOtp(password))
        .build();
  }

  @Nested
  class CreateOtpTests {

    @Test
    void testOtpServiceIsCalled() {

      PerDeviceDataResponse data = buildIosDeviceData(OffsetDateTime.now(), true);
      String password = buildUuid();
      ArgumentCaptor<OneTimePassword> otpCaptor = ArgumentCaptor.forClass(OneTimePassword.class);
      ArgumentCaptor<Integer> validityCaptor = ArgumentCaptor.forClass(Integer.class);

      when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok(jsonify(data)));
      when(jwtProvider.generateJwt()).thenReturn("secretkey");
      ResponseEntity<OtpCreationResponse> response = postOtpCreationRequest(buildValidOtpPayload(password),
          testRestTemplate, IOS_OTP_URL, false);

      verify(otpService, times(1)).createOtp(otpCaptor.capture(), validityCaptor.capture());
      OneTimePassword cptOtp = otpCaptor.getValue();

      ZonedDateTime expectedExpirationTime = ZonedDateTime.now(ZoneOffset.UTC)
          .plusHours(ppacConfiguration.getOtpValidityInHours());
      ZonedDateTime actualExpirationTime = getZonedDateTimeFor(cptOtp.getExpirationTimestamp());

      assertThat(validityCaptor.getValue()).isEqualTo(ppacConfiguration.getOtpValidityInHours());
      assertThat(actualExpirationTime).isEqualToIgnoringSeconds(expectedExpirationTime);
      assertThat(cptOtp.getPassword()).isEqualTo(password);
    }

    @Test
    void testResponseIs400WhenOtpIsInvalidUuid() {
      PerDeviceDataResponse data = buildIosDeviceData(OffsetDateTime.now(), true);
      String password = "invalidUUID";

      when(iosDeviceApiClient.queryDeviceData(anyString(), any())).thenReturn(ResponseEntity.ok(jsonify(data)));
      when(jwtProvider.generateJwt()).thenReturn("secretkey");
      ResponseEntity<OtpCreationResponse> response = postOtpCreationRequest(buildValidOtpPayload(password),
          testRestTemplate, IOS_OTP_URL, false);

      assertThat(response.getStatusCode()).isEqualTo(BAD_REQUEST);
    }
  }
}
