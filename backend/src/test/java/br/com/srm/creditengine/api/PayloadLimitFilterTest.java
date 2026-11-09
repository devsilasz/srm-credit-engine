package br.com.srm.creditengine.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PayloadLimitFilterTest {

  private final PayloadLimitFilter filter = new PayloadLimitFilter(new ObjectMapper());
  private final MockHttpServletResponse response = new MockHttpServletResponse();
  private final MockFilterChain chain = new MockFilterChain();

  /** Corpo chunked: não declara Content-Length. */
  private static MockHttpServletRequest chunked(byte[] body) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/pricing/quote") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(body);
    request.setCharacterEncoding("UTF-8");
    return request;
  }

  @Test
  @DisplayName("Limite exato: 64 KB passam, e o corpo continua legível depois do filtro")
  void exactLimitPassesAndBodyIsStillReadable() throws Exception {
    byte[] body = new byte[PayloadLimitFilter.MAX_BYTES];

    filter.doFilter(chunked(body), response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
    assertThat(forwarded.getInputStream().readAllBytes()).hasSize(PayloadLimitFilter.MAX_BYTES);
  }

  @Test
  @DisplayName("Limite exato: um byte acima, sem Content-Length, é 413 e não chega ao controller")
  void oneByteOverLimitIsRefused() throws Exception {
    filter.doFilter(chunked(new byte[PayloadLimitFilter.MAX_BYTES + 1]), response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void declaredContentLengthOverLimitIsRefusedWithoutReading() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/receivables");
    request.setContent(new byte[PayloadLimitFilter.MAX_BYTES + 1]);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("/api/receivables");
  }

  @Test
  void requestWithoutBodyIsNotBuffered() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/settlements");

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void bufferedBodyWorksThroughReaderAndStream() throws Exception {
    filter.doFilter(chunked("{\"a\":1}".getBytes(StandardCharsets.UTF_8)), response, chain);
    HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();

    assertThat(forwarded.getReader().readLine()).isEqualTo("{\"a\":1}");

    ServletInputStream stream = forwarded.getInputStream();
    assertThat(stream.isReady()).isTrue();
    assertThat(stream.isFinished()).isFalse();
    assertThat(stream.read()).isEqualTo('{');
    assertThat(stream.readAllBytes()).hasSize(6);
    assertThat(stream.isFinished()).isTrue();
    assertThatThrownBy(() -> stream.setReadListener(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
