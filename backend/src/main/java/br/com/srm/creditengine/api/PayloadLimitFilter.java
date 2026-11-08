package br.com.srm.creditengine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limita o corpo a 64 KB. Lê de fato os bytes porque um corpo chunked não declara {@code
 * Content-Length}, e {@code server.tomcat.max-swallow-size} não limita corpo JSON.
 */
@Component
public class PayloadLimitFilter extends OncePerRequestFilter {

  static final int MAX_BYTES = 64 * 1024;
  private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

  private final ObjectMapper objectMapper;

  public PayloadLimitFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !METHODS_WITH_BODY.contains(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > MAX_BYTES) {
      reject(request, response);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
    if (body.length > MAX_BYTES) {
      reject(request, response);
      return;
    }
    chain.doFilter(new BufferedBodyRequest(request, body), response);
  }

  private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    ProblemDetail problem =
        ApiExceptionHandler.problem(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "PAYLOAD_TOO_LARGE",
            "O corpo da requisição passa do limite de 64 KB.");
    problem.setInstance(URI.create(request.getRequestURI()));
    objectMapper.writeValue(response.getOutputStream(), problem);
  }

  private static final class BufferedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    BufferedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream stream = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return stream.read();
        }

        @Override
        public int read(byte[] target, int offset, int length) {
          return stream.read(target, offset, length);
        }

        @Override
        public boolean isFinished() {
          return stream.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("Corpo já está em memória");
        }
      };
    }

    @Override
    public BufferedReader getReader() throws IOException {
      return new BufferedReader(new InputStreamReader(getInputStream(), getCharacterEncoding()));
    }
  }
}
